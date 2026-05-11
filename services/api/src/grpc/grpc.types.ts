import * as grpc from '@grpc/grpc-js'

export interface UnaryCallback<TResponse> {
	(error: grpc.ServiceError | null, response: TResponse): void
}
