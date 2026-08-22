import * as grpc from '@grpc/grpc-js'

export interface UnaryCallback<TResponse> {
	(error: grpc.ServiceError | null, response: TResponse): void
}

const DEFAULT_UNARY_DEADLINE_MS = 10_000

export function callUnary<TResponse>(
	client: grpc.Client | null,
	methodName: string,
	request: object,
	options: grpc.CallOptions = {},
): Promise<TResponse> {
	if (!client) return Promise.reject(new Error('gRPC client is not initialized'))
	const method = client[methodName as keyof grpc.Client] as unknown
	if (typeof method !== 'function') return Promise.reject(new Error(`Unknown gRPC method: ${methodName}`))

	return new Promise<TResponse>((resolve, reject) => method.call(client, request, {
		...options,
		deadline: options.deadline ?? Date.now() + DEFAULT_UNARY_DEADLINE_MS,
	}, (error: grpc.ServiceError | null, response: TResponse) => error ? reject(error) : resolve(response)))
}
