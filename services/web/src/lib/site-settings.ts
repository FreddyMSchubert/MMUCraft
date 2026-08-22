'use client';

import { useEffect, useState } from 'react';

export interface SiteSettings {
	arachnophobiaMode: boolean;
	reduce3dRendering: boolean;
	mysteriousSetting: boolean;
}

export const DEFAULT_SITE_SETTINGS: SiteSettings = {
	arachnophobiaMode: false,
	reduce3dRendering: false,
	mysteriousSetting: false,
};

const STORAGE_KEY = 'mmu-site-settings-v1';
const SETTINGS_EVENT = 'mmu-site-settings-change';

function readSiteSettings(): SiteSettings {
	try {
		const stored = JSON.parse(
			window.localStorage.getItem(STORAGE_KEY) ?? '{}',
		) as Partial<SiteSettings>;
		return {
			arachnophobiaMode: Boolean(stored.arachnophobiaMode),
			reduce3dRendering: Boolean(stored.reduce3dRendering),
			mysteriousSetting: Boolean(stored.mysteriousSetting),
		};
	} catch {
		return DEFAULT_SITE_SETTINGS;
	}
}

export function useSiteSettings() {
	const [settings, setSettings] = useState<SiteSettings>(DEFAULT_SITE_SETTINGS);

	useEffect(() => {
		const refresh = () => {
			setSettings(readSiteSettings());
		};
		refresh();
		window.addEventListener(SETTINGS_EVENT, refresh);
		window.addEventListener('storage', refresh);
		return () => {
			window.removeEventListener(SETTINGS_EVENT, refresh);
			window.removeEventListener('storage', refresh);
		};
	}, []);

	function updateSetting<Key extends keyof SiteSettings>(key: Key, value: SiteSettings[Key]) {
		const next = { ...readSiteSettings(), [key]: value };
		window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
		window.dispatchEvent(new Event(SETTINGS_EVENT));
	}

	return { settings, updateSetting };
}
