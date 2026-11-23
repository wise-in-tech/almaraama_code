import { describe, expect, it } from 'vitest'
import { canOnPlatform, canOnSite, type MyAccess } from './queries'

const access: MyAccess = {
  platformPermissions: ['text:read'],
  organizations: [{ id: 'org', type: 'PROPERTY_DEVELOPER', name: 'P', permissions: ['registration-qr:manage'] }],
  sites: [
    { id: 'site', organizationId: 'org', name: 'S', roles: [], permissions: ['unit:read'], allowedBuildingIds: [], occupiedUnitIds: [] },
  ],
}

describe('menus from the effective permissions (ADR-026)', () => {
  it('ADR026_aSitePermissionOrAnOrganizationPermissionOpensTheMenu', () => {
    expect(canOnSite(access, 'site', 'unit:read')).toBe(true)
    expect(canOnSite(access, 'site', 'registration-qr:manage')).toBe(true)
    expect(canOnSite(access, 'site', 'access:approve')).toBe(false)
    expect(canOnPlatform(access, 'text:read')).toBe(true)
    expect(canOnPlatform(access, 'support:access')).toBe(false)
  })
})
