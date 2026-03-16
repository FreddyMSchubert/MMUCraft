interface StatCardProps {
  label: string
  value: string
}

export function StatCard({ label, value }: StatCardProps) {
  return (
    <section className="card statCard">
      <p className="muted">{label}</p>
      <p className="bigValue">{value}</p>
    </section>
  )
}
