import type * as grpc from '@grpc/grpc-js';

export type UnaryCallback<TResponse> = (
	error: grpc.ServiceError | null,
	response: TResponse,
) => void;

const DEFAULT_UNARY_DEADLINE_MS = 10_000;

export function callUnary<TResponse>(
	client: grpc.Client | null,
	methodName: string,
	request: object,
	options: grpc.CallOptions = {},
): Promise<TResponse> {
	if (!client) return Promise.reject(new Error('gRPC client is not initialized'));
	const method = client[methodName as keyof grpc.Client] as unknown;
	if (typeof method !== 'function')
		return Promise.reject(new Error(`Unknown gRPC method: ${methodName}`));

	return new Promise<TResponse>((resolve, reject) => {
		Reflect.apply(method, client, [
			request,
			{
				...options,
				deadline: options.deadline ?? Date.now() + DEFAULT_UNARY_DEADLINE_MS,
			},
			(error: grpc.ServiceError | null, response: TResponse) => {
				if (error) reject(error);
				else resolve(response);
			},
		]);
	});
}
