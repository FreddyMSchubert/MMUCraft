import { Injectable } from '@nestjs/common';
import { filter, interval, map, merge, of, Subject } from 'rxjs';

@Injectable()
export class DailyTaskUpdateEventsService {
	private readonly taskEvents = new Subject<{ userId: number }>();

	notifyUser(userId: number) {
		this.taskEvents.next({ userId });
	}

	forUser(userId: number) {
		return merge(
			of({ data: { type: 'ready' } }),
			this.taskEvents.pipe(
				filter((event) => event.userId === userId),
				map(() => ({ data: { type: 'daily-update' } })),
			),
			interval(15_000).pipe(map(() => ({ data: { type: 'ping' } }))),
		);
	}
}
