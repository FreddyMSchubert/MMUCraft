allow_k8s_contexts('k3d-mc-dev')

default_registry(
	'localhost:12345',
	host_from_cluster = 'mc-dev-registry:5000',
)

k8s_yaml([
	'k8s/00-namespace.yaml',
	'k8s/10-rabbitmq.yaml',
	'k8s/20-api.yaml',
	'k8s/30-web.yaml',
	'k8s/40-minecraft.yaml',
])

docker_build(
	'mcstack/api',
	'services/api',
	dockerfile = 'services/api/Dockerfile',
	live_update = [
		fall_back_on('services/api/package.json'),
		sync('services/api/src', '/app/src'),
	],
	ignore = ['node_modules'],
)

docker_build(
	'mcstack/web',
	'services/web',
	dockerfile = 'services/web/Dockerfile',
	live_update = [
		fall_back_on('services/web/package.json'),
		sync('services/web/app', '/app/app'),
		sync('services/web/public', '/app/public'),
	],
	ignore = ['node_modules', '.next'],
)

docker_build(
	'mcstack/minecraft-server',
	'minecraft/main/mod/build/libs',
	dockerfile = 'minecraft/main/Dockerfile',
	ignore = [
		'*-sources.jar',
	],
)

k8s_resource(workload = 'rabbitmq', port_forwards = [5672, 15672])
k8s_resource(workload = 'api', port_forwards = [8080], resource_deps = ['rabbitmq'])
k8s_resource(workload = 'web', port_forwards = [3000], resource_deps = ['api'])
k8s_resource(workload = 'minecraft', port_forwards = [25565], resource_deps = ['rabbitmq', 'api'])
