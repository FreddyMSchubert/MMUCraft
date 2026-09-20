'use client';

import { usePathname, useSearchParams } from 'next/navigation';
import { useEffect } from 'react';

export function ReferralTracking() {
	const pathname = usePathname();
	const search = useSearchParams().toString();

	useEffect(() => {
		const url = new URL(window.location.href);
		const incoming = url.searchParams.get('referral');
		if (incoming && /^[a-f0-9]{32}$/.test(incoming)) {
			window.sessionStorage.setItem('referral', incoming);
		} else {
			const saved = window.sessionStorage.getItem('referral');
			if (saved && /^[a-f0-9]{32}$/.test(saved) && !incoming) {
				url.searchParams.set('referral', saved);
				window.history.replaceState(null, '', url);
			}
		}
	}, [pathname, search]);

	return null;
}
