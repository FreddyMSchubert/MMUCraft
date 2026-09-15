const MANCHESTER_DATE_TIME = new Intl.DateTimeFormat('en-GB', {
	timeZone: 'Europe/London',
	dateStyle: 'long',
	timeStyle: 'short',
});

export function formatManchesterDateTime(timestamp: number) {
	return MANCHESTER_DATE_TIME.format(new Date(timestamp));
}
