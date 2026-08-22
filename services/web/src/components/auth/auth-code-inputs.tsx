import { AUTH_CODE_ITEMS } from './authentication-flow';

export function AuthCodeInputs({
	value,
	onChange,
}: {
	value: string[];
	onChange: (value: string[]) => void;
}) {
	return (
		<div className="authCodeInputs" role="group" aria-label="Three-item verification code">
			{value.map((selected, index) => (
				<label className="authCodeInput" key={index}>
					<span className="authCodePosition">{index + 1}</span>
					<AuthCodeIcon itemName={selected} />
					<select
						value={selected}
						onChange={(event) => {
							onChange(
								value.map((item, itemIndex) =>
									itemIndex === index ? event.target.value : item,
								),
							);
						}}
						aria-label={`Code item ${index + 1}`}
						required
					>
						<option value="">Choose an item</option>
						{AUTH_CODE_ITEMS.map((item) => (
							<option value={item.name} key={item.name}>
								{item.name}
							</option>
						))}
					</select>
				</label>
			))}
		</div>
	);
}

function AuthCodeIcon({ itemName }: { itemName: string }) {
	const item = AUTH_CODE_ITEMS.find((candidate) => candidate.name === itemName);
	if (!item)
		return (
			<span className="authCodeIcon authCodeIconEmpty" aria-hidden="true">
				?
			</span>
		);
	return (
		<span
			className={`authCodeIcon${'head' in item ? ' authCodeHeadIcon' : ''}`}
			style={{ backgroundImage: `url(${item.image})` }}
			role="img"
			aria-label={'head' in item ? `${item.name} head` : item.name}
		/>
	);
}
