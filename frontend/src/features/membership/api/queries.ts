import { useQuery } from '@tanstack/react-query'
import { api, unwrap, type components } from '@/shared/api'

/** What the signed-in person can see and do (GET /v1/me/access), with their open support session (ADR-035). */
export type MyAccess = components['schemas']['MyAccess']
/** One access of a person to a site (contract {@code SiteMembership}). */
export type SiteMembership = components['schemas']['SiteMembership']
/** A pre-registration, never its key (contract {@code PreRegistration}). */
export type PreRegistration = components['schemas']['PreRegistration']
/** A permission code ({@code unit:import}). */
export type PermissionCode = components['schemas']['PermissionCode']

/** The caller's perimeter for the menus (the server still checks every request). */
export function useMyAccess() {
  return useQuery({ queryKey: ['me', 'access'], queryFn: () => unwrap(api.GET('/v1/me/access')) })
}

/** The caller's site accesses with their units (D2, D6). */
export function useMyMemberships() {
  return useQuery({ queryKey: ['me', 'memberships'], queryFn: () => unwrap(api.GET('/v1/me/memberships')) })
}

/** Whether the person holds a permission on a site, from their effective permissions (menus only). */
export function canOnSite(access: MyAccess | undefined, siteId: string, permission: PermissionCode): boolean {
  if (!access) return false
  const site = access.sites.find((s) => s.id === siteId)
  if (site?.permissions.includes(permission)) return true
  const organizationId = site?.organizationId
  return access.organizations.some(
    (o) => (organizationId === undefined || o.id === organizationId) && o.permissions.includes(permission),
  )
}

/** Whether the person holds a platform permission (back-office menus). */
export function canOnPlatform(access: MyAccess | undefined, permission: PermissionCode): boolean {
  return access?.platformPermissions.includes(permission) ?? false
}
