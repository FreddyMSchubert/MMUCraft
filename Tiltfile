allow_k8s_contexts('k3d-mc-dev')

default_registry(
    'localhost:12345',
    host_from_cluster='mc-dev-registry:5000',
)

k8s_yaml([
    'k8s/00-namespace.yaml',
    'k8s/20-api.yaml',
    'k8s/30-web.yaml',
    'k8s/40-minecraft.yaml',
])

docker_build(
    'mcstack/api',
    '.',
    dockerfile='services/api/Dockerfile',
    live_update=[
        fall_back_on('services/api/package.json'),
        fall_back_on('services/api/package-lock.json'),
        fall_back_on('services/api/tsconfig.json'),
        fall_back_on('proto/auth.proto'),
		fall_back_on('proto/gameplay.proto'),
        sync('services/api/src', '/app/src'),
        sync('services/web/public/knowledge', '/app/content/knowledge'),
        sync('minecraft/main/data/data/items', '/app/content/items'),
        sync('proto', '/app/proto'),
    ],
    ignore=[
        '.git',
        '.github',
        '.vscode',
        'assets',
        'k8s',
        'minecraft/main/mod',
        'minecraft/main/respack',
        'third_party_licenses',
        'README.md',
        'Tiltfile',
        'package-lock.json',
        'services/api/node_modules',
        'services/api/data',
        'services/api/dist',
    ],
)

docker_build(
    'mcstack/web',
    'services/web',
    dockerfile='services/web/Dockerfile',
    target='dev',
    live_update=[
        fall_back_on('services/web/package.json'),
        fall_back_on('services/web/package-lock.json'),
        fall_back_on('services/web/next.config.ts'),
        fall_back_on('services/web/tsconfig.json'),
        fall_back_on('services/web/eslint.config.mjs'),
        fall_back_on('services/web/.env'),
        fall_back_on('services/web/.env.example'),
        sync('services/web/src', '/app/src'),
        sync('services/web/public', '/app/public'),
    ],
    ignore=['node_modules', '.next'],
)

custom_build(
    'mcstack/minecraft-server',
    './minecraft/main/build-minecraft-image.sh "$EXPECTED_REF"',
    command_bat='powershell -NoProfile -ExecutionPolicy Bypass -File .\\minecraft\\main\\build-minecraft-image.ps1 %EXPECTED_REF%',
    deps=[
        'minecraft/main/build-minecraft-image.sh',
        'minecraft/main/build-minecraft-image.ps1',
        'minecraft/main/mod/src',
        'minecraft/main/mod/build.gradle',
        'proto/auth.proto',
        'proto/gameplay.proto',
    ],
)

k8s_resource(workload='api', port_forwards=[8080, 50051])
k8s_resource(workload='web', port_forwards=[3000], resource_deps=['api'])
k8s_resource(
    workload='minecraft',
    port_forwards=[25565, 50052],
    resource_deps=['api'],
    trigger_mode=TRIGGER_MODE_MANUAL,
    auto_init=False,
)
