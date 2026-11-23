import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/api'

/** The signed-in person (GET /v1/me). */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      const { data, error } = await api.GET('/v1/me')
      if (error) throw error
      return data
    },
  })
}
