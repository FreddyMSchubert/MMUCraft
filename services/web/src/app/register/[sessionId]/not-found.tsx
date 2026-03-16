import Link from 'next/link'

export default function RegistrationNotFound() {
  return (
    <section className="stack narrow">
      <div className="hero">
        <div className="eyebrow">registration</div>
        <h2>That registration session is gone.</h2>
        <p className="muted">Ask the Minecraft server for a fresh /auth link.</p>
        <div className="linkRow" style={{ marginTop: '16px' }}>
          <Link className="button" href="/">
            Back to home
          </Link>
        </div>
      </div>
    </section>
  )
}
