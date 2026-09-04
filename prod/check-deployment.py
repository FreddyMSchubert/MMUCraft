#!/usr/bin/env python3
"""Check deployment control flow without Docker or a running server."""

import os
from pathlib import Path
import subprocess
import tempfile

DEPLOY = Path(__file__).with_name('deploy.sh').resolve()

# These commands record deployment actions and supply controlled service responses.
FAKE = r'''#!/usr/bin/env python3
import os
from pathlib import Path
import sys

name = Path(sys.argv[0]).name
args = sys.argv[1:]
mode = os.environ['CHECK_MODE']
if name == 'date':
    clock = Path('clock')
    now = int(clock.read_text()) + 1 if clock.exists() else 1700000000
    clock.write_text(str(now))
    print(now)
elif name == 'sleep':
    state = Path('data/velocity/deployment.properties')
    if state.exists() and (mode not in ('no_ack', 'legacy_empty') or Path('proxy_started').exists()):
        values = dict(line.split('=', 1) for line in state.read_text().splitlines())
        players = 'false' if mode == 'empty' else 'true'
        ready = 'false' if mode == 'not_ready' else 'true'
        Path('data/velocity/deployment-drained').write_text(values['id'] + ' ' + players + ' ' + ready)
else:
    with Path('commands').open('a') as log:
        log.write(repr(args) + '\n')
    if args[0] == 'run':
        print('online-mode=false')
    elif args[0] == 'compose':
        args = args[5:]
        if args[0] == 'ps':
            print('api\nminecraft\nvelocity')
        elif args[0] == 'up':
            if args[-1] == 'velocity':
                Path('proxy_started').touch()
            elif mode == 'unhealthy':
                sys.exit(1)
        elif args[0] == 'exec' and '/api/internal/deployment/start' in args:
            sys.exit(1) if mode == 'notice_failure' else print('false' if mode == 'legacy_empty' else 'true')
        elif args[0] == 'exec' and '/api/internal/shutdown' in args:
            sys.exit(1) if mode == 'save_failure' else print('{"ready":true}')
        elif args[0] == 'exec':
            print('true')
'''


def check(mode, force, succeeds, stays_updating):
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        bin_dir = root / 'bin'
        bin_dir.mkdir()
        for name in ('docker', 'sleep', 'date'):
            command = bin_dir / name
            command.write_text(FAKE)
            command.chmod(0o755)
        (root / '.env').write_text(
            'PUBLIC_URL=https://example.org\nRESEND_API_KEY=check\nRESEND_FROM=check\n'
            + ''.join(f'{key}={"x" * 32}\n' for key in (
                'AUTH_CODE_SECRET', 'GRAFANA_ADMIN_PASSWORD',
                'VELOCITY_API_SECRET', 'VELOCITY_FORWARDING_SECRET',
            ))
        )
        result = subprocess.run(
            ['sh', str(DEPLOY), 'dev', 'ghcr.io/example/server', '0', str(force).lower()],
            cwd=root,
            env={**os.environ, 'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'], 'CHECK_MODE': mode},
            capture_output=True, text=True, timeout=15,
        )
        assert (result.returncode == 0) == succeeds, (mode, force, result.stdout, result.stderr)
        state = (root / 'data/velocity/deployment.properties').read_text()
        assert ('updating=true' in state) == stays_updating, (mode, force, state)
        commands = (root / 'commands').read_text()
        notices = '/api/internal/deployment/start' in commands
        completed = '/api/internal/deployment/complete' in commands
        stopped = "'stop', 'minecraft', 'api'" in commands
        assert notices == (mode != 'empty'), (mode, commands)
        assert completed == (succeeds and mode not in ('empty', 'legacy_empty')), (mode, commands)
        if succeeds:
            assert stopped, commands
            assert commands.index("'pull'") < commands.index('mc-send-to-console'), commands
            if notices:
                assert commands.index('/deployment/start') < commands.index('/internal/shutdown'), commands
        elif mode in ('no_ack', 'legacy_empty', 'notice_failure', 'save_failure'):
            assert not stopped, commands
        if mode == 'save_failure' and not force:
            assert "'start', '--wait'" in commands, commands
            assert "'up', '-d', '--remove-orphans'" not in commands, commands
        for command in commands.splitlines():
            if '--force-recreate' in command:
                assert "'prometheus', 'grafana', 'loki', 'alloy', 'nginx'" in command, command
                assert "'velocity'" not in command, command


for check_mode in ('players', 'empty', 'no_ack', 'legacy_empty', 'notice_failure', 'save_failure', 'unhealthy', 'not_ready'):
    for forced in (False, True):
        success = check_mode in ('players', 'empty') or (
            forced and check_mode in ('no_ack', 'legacy_empty', 'notice_failure', 'save_failure')
        )
        check(check_mode, forced, success, check_mode in ('unhealthy', 'not_ready'))
print('Deployment checks passed (16 scenarios).')
