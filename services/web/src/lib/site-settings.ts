'use client';

import { useEffect, useState } from 'react';

export type CosmeticPreviewView = 'cosmetic' | 'item-frame' | 'player';

export interface SiteSettings {
	arachnophobiaMode: boolean;
	cosmeticPreviewView: CosmeticPreviewView;
	dyslexiaMode: boolean;
	reduce3dRendering: boolean;
	mysteriousSetting: boolean;
}

export const DEFAULT_SITE_SETTINGS: SiteSettings = {
	arachnophobiaMode: false,
	cosmeticPreviewView: 'cosmetic',
	dyslexiaMode: false,
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
			cosmeticPreviewView:
				stored.cosmeticPreviewView === 'item-frame' ||
				stored.cosmeticPreviewView === 'player'
					? stored.cosmeticPreviewView
					: 'cosmetic',
			dyslexiaMode: Boolean(stored.dyslexiaMode),
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

export function SiteSettingsEffects() {
	useEffect(() => {
		const apply = () => {
			document.documentElement.toggleAttribute(
				'data-dyslexia-mode',
				readSiteSettings().dyslexiaMode,
			);
		};
		apply();
		window.addEventListener(SETTINGS_EVENT, apply);
		window.addEventListener('storage', apply);
		return () => {
			window.removeEventListener(SETTINGS_EVENT, apply);
			window.removeEventListener('storage', apply);
		};
	}, []);

	return null;
}
