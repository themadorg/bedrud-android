import { createFileRoute } from '@tanstack/react-router'
import { ErrorPage } from '@/components/ErrorPage'

export const Route = createFileRoute('/$')({
  component: NotFound,
})

function NotFound() {
  return <ErrorPage variant="not-found" />
}
