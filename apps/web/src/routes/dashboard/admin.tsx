import { createFileRoute, Outlet, redirect, useNavigate } from '@tanstack/react-router'
import { createContext, useContext, useEffect, useMemo } from 'react'
import { api } from '#/lib/api'
import type { User } from '#/lib/user.store'
import { useUserStore } from '#/lib/user.store'

export const Route = createFileRoute('/dashboard/admin')({
  // Loader confirms admin status (after ensuring user is loaded).
  // This prevents non-admin flash of admin UI that the old beforeLoad allowed
  // when user was still null during initial hydration.
  loader: async () => {
    if (typeof window === 'undefined') return

    // Ensure we have a fresh user (similar to parent dashboard loader)
    let user = useUserStore.getState().user
    if (!user) {
      try {
        const u = await api.get<User & { accesses?: string[] }>('/api/auth/me')
        useUserStore.getState().setUser({
          id: u.id,
          email: u.email,
          name: u.name,
          provider: u.provider,
          isSuperAdmin: u.accesses?.includes('superadmin') ?? false,
          isAdmin: (u.accesses?.includes('admin') || u.accesses?.includes('superadmin')) ?? false,
          accesses: u.accesses ?? [],
          avatarUrl: u.avatarUrl,
        })
        user = useUserStore.getState().user
      } catch {
        throw redirect({ to: '/auth' })
      }
    }

    const accesses = user?.accesses ?? []
    const canAccess = user?.isSuperAdmin || accesses.includes('admin') || accesses.includes('moderator')
    if (!canAccess) {
      throw redirect({ to: '/dashboard' })
    }
  },
  staleTime: Infinity,
  component: AdminGuard,
})

interface AdminContextValue {
  isReadOnly: boolean
  isModerator: boolean
  currentUserId: string | null
}

const AdminContext = createContext<AdminContextValue>({ isReadOnly: false, isModerator: false, currentUserId: null })

export function useAdminContext() {
  return useContext(AdminContext)
}

function AdminGuard() {
  const user = useUserStore((s) => s.user)
  const navigate = useNavigate()
  const accesses = user?.accesses ?? []
  const isModerator = accesses.includes('moderator')
  const canAccess = user?.isSuperAdmin || accesses.includes('admin') || isModerator

  useEffect(() => {
    if (user !== null && !canAccess) {
      navigate({ to: '/dashboard' })
    }
  }, [user, canAccess, navigate])

  const ctx = useMemo<AdminContextValue>(
    () => ({ isReadOnly: isModerator, isModerator, currentUserId: user?.id ?? null }),
    [isModerator, user?.id],
  )

  return (
    <AdminContext.Provider value={ctx}>
      <Outlet />
    </AdminContext.Provider>
  )
}
