'use client'

import Link from 'next/link'
import { FormEvent, useState } from 'react'
import { SiteSettings, useSiteSettings } from '@/lib/site-settings'

type MiscSection = 'settings' | 'gift-codes'

export function MiscTab({ section }: { section?: string }) {
	const activeSection: MiscSection = section === 'gift-codes' ? 'gift-codes' : 'settings'
	return <div className="miscPanel">
		<nav className="miscSubTabs" aria-label="Miscellaneous sections">
			<Link className={activeSection === 'settings' ? 'active' : ''} href="/play/misc/settings">Settings</Link>
			<Link className={activeSection === 'gift-codes' ? 'active' : ''} href="/play/misc/gift-codes">Redeem gift code</Link>
		</nav>
		{activeSection === 'settings' ? <SettingsSection /> : <GiftCodeSection />}
	</div>
}

function SettingsSection() {
	const { settings, updateSetting } = useSiteSettings()
	return <section className="settingsSection">
		<h3>Settings</h3>
		<div className="settingsList">
			<SettingToggle setting="arachnophobiaMode" checked={settings.arachnophobiaMode} onChange={updateSetting} title="Arachnophobia mode" description="Fully hides all images with spider-related stuff from the website. You may still encounter spidery things in-game." />
			<SettingToggle setting="reduce3dRendering" checked={settings.reduce3dRendering} onChange={updateSetting} title="Reduce 3D rendering" description="Only loads 3D item models inside the detail view." />
			<SettingToggle setting="mysteriousSetting" checked={settings.mysteriousSetting} onChange={updateSetting} title="the very mysterious and highly preculiar, though irregularly occurring, setting" />
		</div>
	</section>
}

function SettingToggle<Key extends keyof SiteSettings>({ setting, checked, onChange, title, description }: { setting: Key; checked: boolean; onChange: (key: Key, value: SiteSettings[Key]) => void; title: string; description?: string }) {
	return <label className="settingToggle"><span><strong>{title}</strong>{description && <small>{description}</small>}</span><input type="checkbox" checked={checked} onChange={(event) => onChange(setting, event.target.checked as SiteSettings[Key])} /><i aria-hidden="true" /></label>
}

function GiftCodeSection() {
	const [code, setCode] = useState('')
	const [busy, setBusy] = useState(false)
	const [message, setMessage] = useState('')
	const [error, setError] = useState('')

	function redeem(event: FormEvent) {
		event.preventDefault()
		setBusy(true)
		setMessage('')
		setError('')
		void (async () => {
			try {
				const response = await fetch('/api/gift-codes/redeem', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code }) })
				const body = await response.json().catch(() => null)
				if (!response.ok) throw new Error(typeof body?.message === 'string' ? body.message : 'Could not redeem gift code')
				setCode('')
				setMessage(body?.message ?? 'Gift code redeemed.')
			} catch (caught) {
				setError(caught instanceof Error ? caught.message : 'Could not redeem gift code')
			} finally {
				setBusy(false)
			}
		})()
	}

	return <section className="giftCodeSection">
		<div><h3>Gift codes</h3><p>Enter a gift code while online to receive dabloons in-game.</p></div>
		<div className="giftInstructions"><p>Codes may expire or be first-come, first-served. Failed offline attempts do not use the code.</p></div>
		<form className="redeemForm" onSubmit={redeem}><label htmlFor="gift-code">Gift code</label><div><input id="gift-code" value={code} onChange={(event) => setCode(event.target.value)} placeholder="Enter your code" maxLength={64} required /><button disabled={busy}>{busy ? 'Redeeming...' : 'Redeem'}</button></div></form>
		{message && <p className="adminMessage">{message}</p>}{error && <p className="authError">{error}</p>}
	</section>
}
