'use client';

import {
	createContext,
	isValidElement,
	useCallback,
	useContext,
	useEffect,
	useRef,
	useState,
	type CSSProperties,
	type ReactNode,
} from 'react';
import { decorateDabloonText } from '@/components/dabloon-amount';

type AlertTone = 'info' | 'success' | 'danger';
type ButtonTone = 'primary' | 'secondary' | 'danger';

export interface AlertButton {
	label: ReactNode;
	value?: string;
	tone?: ButtonTone;
	fillColor?: string;
	outlineColor?: string;
	textColor?: string;
	onClick?: () => void;
}

export interface AlertOptions {
	title?: string;
	message: ReactNode;
	tone?: AlertTone;
	buttons?: AlertButton[];
}

interface ConfirmOptions extends Omit<AlertOptions, 'buttons'> {
	confirmLabel?: ReactNode;
	cancelLabel?: ReactNode;
	confirmTone?: ButtonTone;
}

interface AlertRequest {
	id: number;
	options: AlertOptions;
	resolve: (value: string | undefined) => void;
}

interface AlertContextValue {
	showAlert: (options: ReactNode | AlertOptions) => Promise<string | undefined>;
	confirm: (options: ReactNode | ConfirmOptions) => Promise<boolean>;
}

const AlertContext = createContext<AlertContextValue | null>(null);

export function SiteAlertProvider({ children }: { children: ReactNode }) {
	const dialog = useRef<HTMLDialogElement>(null);
	const activeRef = useRef<AlertRequest | null>(null);
	const queue = useRef<AlertRequest[]>([]);
	const nextId = useRef(0);
	const closingRef = useRef(false);
	const [active, setActive] = useState<AlertRequest | null>(null);
	const [closing, setClosing] = useState(false);

	const showAlert = useCallback((input: ReactNode | AlertOptions) => {
		const options = isAlertOptions(input) ? input : { message: input };
		return new Promise<string | undefined>((resolve) => {
			const request = { id: ++nextId.current, options, resolve };
			if (activeRef.current) {
				queue.current.push(request);
				return;
			}
			activeRef.current = request;
			setActive(request);
		});
	}, []);

	const confirm = useCallback(
		async (input: ReactNode | ConfirmOptions) => {
			const options: ConfirmOptions = isConfirmOptions(input) ? input : { message: input };
			return (
				(await showAlert({
					...options,
					buttons: [
						{
							label: options.cancelLabel ?? 'Cancel',
							value: 'cancel',
							tone: 'secondary',
						},
						{
							label: options.confirmLabel ?? 'Confirm',
							value: 'confirm',
							tone: options.confirmTone ?? 'primary',
						},
					],
				})) === 'confirm'
			);
		},
		[showAlert],
	);

	const choose = useCallback(
		(button: AlertButton) => {
			if (!active || closingRef.current) return;
			closingRef.current = true;
			setClosing(true);
			window.setTimeout(() => {
				dialog.current?.close();
				button.onClick?.();
				active.resolve(button.value);
				const next = queue.current.shift() ?? null;
				activeRef.current = next;
				closingRef.current = false;
				setClosing(false);
				setActive(next);
			}, 180);
		},
		[active],
	);

	useEffect(() => {
		if (active && dialog.current && !dialog.current.open) dialog.current.showModal();
	}, [active]);

	const buttons = active?.options.buttons?.length
		? active.options.buttons
		: [{ label: 'OK', value: 'ok', tone: 'primary' as const }];
	const tone = active?.options.tone ?? 'info';

	return (
		<AlertContext value={{ showAlert, confirm }}>
			{children}
			{active && (
				<dialog
					key={active.id}
					ref={dialog}
					className={`siteAlert siteAlert-${tone}${closing ? ' closing' : ''}`}
					aria-labelledby={`site-alert-title-${active.id}`}
					aria-describedby={`site-alert-message-${active.id}`}
					onCancel={(event) => {
						event.preventDefault();
						choose(buttons.find((button) => button.value === 'cancel') ?? buttons[0]);
					}}
					onKeyDown={(event) => {
						if (event.key !== 'Escape') return;
						event.preventDefault();
						choose(buttons.find((button) => button.value === 'cancel') ?? buttons[0]);
					}}
				>
					<div className="siteAlertGlow" aria-hidden="true" />
					<div className="siteAlertHeading">
						<span className="siteAlertIcon" aria-hidden="true">
							{tone === 'success' ? '✓' : tone === 'danger' ? '!' : 'i'}
						</span>
						<h2 id={`site-alert-title-${active.id}`}>
							{decorateDabloonText(active.options.title ?? defaultTitle(tone))}
						</h2>
					</div>
					<div className="siteAlertMessage" id={`site-alert-message-${active.id}`}>
						{decorateDabloonText(active.options.message)}
					</div>
					<div className="siteAlertActions">
						{buttons.map((button, index) => (
							<button
								key={index}
								type="button"
								className={`siteAlertButton siteAlertButton-${button.tone ?? 'primary'}`}
								style={buttonStyle(button)}
								autoFocus={index === 0}
								onClick={() => {
									choose(button);
								}}
							>
								{decorateDabloonText(button.label)}
							</button>
						))}
					</div>
				</dialog>
			)}
		</AlertContext>
	);
}

export function useSiteAlert() {
	const value = useContext(AlertContext);
	if (!value) throw new Error('useSiteAlert must be used within SiteAlertProvider');
	return value;
}

function isAlertOptions(input: ReactNode | AlertOptions): input is AlertOptions {
	return hasMessage(input);
}

function isConfirmOptions(input: ReactNode | ConfirmOptions): input is ConfirmOptions {
	return hasMessage(input);
}

function hasMessage(input: unknown) {
	return (
		typeof input === 'object' && input !== null && !isValidElement(input) && 'message' in input
	);
}

function defaultTitle(tone: AlertTone) {
	if (tone === 'success') return 'All done';
	if (tone === 'danger') return 'Something went wrong';
	return 'Heads up';
}

function buttonStyle(button: AlertButton) {
	return {
		'--alert-button-fill': button.fillColor,
		'--alert-button-outline': button.outlineColor,
		'--alert-button-text': button.textColor,
	} as CSSProperties;
}
