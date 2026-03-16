import Link from 'next/link'

export default function NotFound() {
  return (
    <section className="stack narrow">
      <div className="hero">
        <div className="eyebrow">404</div>
        <h2>That page does not exist.</h2>
        <p className="muted">The route is missing, or the registration session has expired and wandered off.</p>
        <div className="linkRow" style={{ marginTop: '16px' }}>
          <Link className="button" href="/">
            Back to home
          </Link>
        </div>
      </div>
    </section>
  )
}
