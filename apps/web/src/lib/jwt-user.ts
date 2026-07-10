import { jwtDecode } from 'jwt-decode'

interface BedrudJwt {
  userId?: string
  accesses?: string[]
}

export function decodeBedrudJwt(token: string | undefined | null): { userId: string; accesses: string[] } {
  if (!token) return { userId: '', accesses: [] }
  try {
    const payload = jwtDecode<BedrudJwt>(token)
    return { userId: payload.userId ?? '', accesses: payload.accesses ?? [] }
  } catch {
    return { userId: '', accesses: [] }
  }
}
