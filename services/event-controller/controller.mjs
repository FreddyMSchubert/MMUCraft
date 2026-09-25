import http from 'node:http';
import { readFile } from 'node:fs/promises';

const name = process.env.EVENT_CONTAINER_NAME || 'kubecraft-surprising-saturday-1';
const api = process.env.API_BASE_URL || 'http://api:8080';
const secret = process.env.INTERNAL_API_SECRET;
const statePath = process.env.EVENT_CONTROL_STATE_PATH || '/state/event-control.json';
let lastError = '';
let busy = false;
let emptySince = 0;

function docker(method, path) {
	return new Promise((resolve, reject) => {
		const request = http.request({ socketPath: '/var/run/docker.sock', path, method, timeout: 10_000 }, (response) => {
			let body = '';
			response.setEncoding('utf8');
			response.on('data', (chunk) => { body += chunk; });
			response.on('end', () => {
				if (response.statusCode >= 200 && response.statusCode < 300) resolve(body ? JSON.parse(body) : null);
				else reject(new Error(`Docker ${method} ${path}: ${response.statusCode} ${body.slice(0, 200)}`));
			});
		});
		request.on('timeout', () => request.destroy(new Error('Docker request timed out')));
		request.on('error', reject);
		request.end();
	});
}

async function reconcile() {
	if (busy) return;
	busy = true;
	try {
		const path = `/containers/${encodeURIComponent(name)}`;
		const container = await docker('GET', `${path}/json`);
		const labels = container.Config.Labels;
		if (labels['com.docker.compose.project'] !== 'kubecraft' || labels['com.docker.compose.service'] !== 'surprising-saturday')
			throw new Error('Event container labels do not match the expected Compose service');
		const cached = await readFile(statePath, 'utf8').then((text) => JSON.parse(text).desiredRunning).catch(() => null);
		let running = container.State.Running;
		if (cached === true && !running) {
			await docker('POST', `${path}/start`);
			console.log('Started Surprising Saturday');
			running = true;
		}
		const response = await fetch(`${api}/api/internal/velocity/event-control`, {
			headers: { authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(5_000),
		});
		if (!response.ok) throw new Error(`Event control API returned ${response.status}`);
		const state = await response.json();
		emptySince = !state.desiredRunning && state.canStop ? emptySince || Date.now() : 0;
		if (state.desiredRunning && !running) {
			await docker('POST', `${path}/start`);
			console.log('Started Surprising Saturday');
		} else if (emptySince && Date.now() - emptySince >= 5_000 && running) {
			await docker('POST', `${path}/stop?t=120`);
			console.log('Stopped Surprising Saturday after player transfer');
		}
		lastError = '';
	} catch (error) {
		const message = String(error);
		if (message !== lastError) console.error(message);
		lastError = message;
	} finally {
		busy = false;
	}
}

if (!secret) throw new Error('INTERNAL_API_SECRET is required');
await reconcile();
setInterval(reconcile, 3_000);
