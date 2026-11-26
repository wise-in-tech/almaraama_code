import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useMe } from '@/features/identity'
import { canOnPlatform, canOnSite, useMyAccess, type MyAccess } from '@/features/membership'
import { Card, Loading } from '@/shared/ui'

/** Style of the links of the home page, shown as buttons. */
const TILE = 'rounded-lg border border-line bg-surface px-3 py-2 text-sm font-semibold hover:border-brand'

/**
 * Home page: the person's organizations, residences and back-office, with the links their permissions open (from
 * {@code /v1/me/access}; the server still checks every request). A resident finds their accesses and units.
 */
export function HomePage() {
  const { t } = useTranslation()
  const me = useMe()
  const access = useMyAccess()
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">
        {me.data ? t('session.greeting', { name: me.data.firstName }) : t('home.title')}
      </h1>
      {access.isPending && <Loading />}
      {access.data && <AdminCard access={access.data} />}
      {access.data?.organizations.map((organization) => (
        <Card key={organization.id} title={organization.name}>
          <div className="flex flex-wrap gap-2">
            <Link className={TILE} to="/organizations/$organizationId/sites" params={{ organizationId: organization.id }}>{t('nav.sites')}</Link>
            <Link className={TILE} to="/organizations/$organizationId/unit-categories" params={{ organizationId: organization.id }}>{t('nav.categories')}</Link>
            {organization.permissions.includes('account:read') && (
              <Link className={TILE} to="/organizations/$organizationId/people" params={{ organizationId: organization.id }}>{t('nav.people')}</Link>
            )}
          </div>
        </Card>
      ))}
      {access.data?.sites.map((site) => {
        const can = (permission: Parameters<typeof canOnSite>[2]) => canOnSite(access.data, site.id, permission)
        const manages = can('unit:read') || can('access:approve') || can('registration-qr:read')
        return (
          <Card key={site.id} title={site.name}>
            <div className="flex flex-wrap gap-2">
              {manages && <Link className={TILE} to="/sites/$siteId" params={{ siteId: site.id }}>{t('nav.site')}</Link>}
              {can('unit:read') && <Link className={TILE} to="/sites/$siteId/units" params={{ siteId: site.id }}>{t('nav.units')}</Link>}
              {can('access:approve') && <Link className={TILE} to="/sites/$siteId/memberships" params={{ siteId: site.id }}>{t('nav.requests')}</Link>}
              {can('pre-registration:read') && (
                <Link className={TILE} to="/sites/$siteId/pre-registrations" params={{ siteId: site.id }}>{t('nav.preRegistrations')}</Link>
              )}
              {can('registration-qr:read') && <Link className={TILE} to="/sites/$siteId/registration-qr" params={{ siteId: site.id }}>{t('nav.qr')}</Link>}
              {can('ticket:create') && <Link className={TILE} to="/sites/$siteId/report" params={{ siteId: site.id }}>{t('nav.report')}</Link>}
              {can('asset:read') && <Link className={TILE} to="/sites/$siteId/assets" params={{ siteId: site.id }}>{t('nav.assets')}</Link>}
              {can('issue-type:manage') && <Link className={TILE} to="/sites/$siteId/issue-types" params={{ siteId: site.id }}>{t('nav.routing')}</Link>}
              {site.occupiedUnitIds.length > 0 && <Link className={TILE} to="/me/accesses">{t('nav.myAccesses')}</Link>}
            </div>
          </Card>
        )
      })}
      <Card>
        <div className="flex flex-wrap gap-2">
          <Link className={TILE} to="/tickets">{t('nav.tickets')}</Link>
          <Link className={TILE} to="/me/accesses">{t('nav.myAccesses')}</Link>
        </div>
      </Card>
    </section>
  )
}

/** The back-office links of a platform administrator (C12, ADR-035). */
function AdminCard({ access }: { access: MyAccess }) {
  const { t } = useTranslation()
  const links = [
    canOnPlatform(access, 'organization:read') && <Link className={TILE} key="o" to="/admin/organizations">{t('nav.adminOrganizations')}</Link>,
    canOnPlatform(access, 'reference-data:read') && <Link className={TILE} key="z" to="/admin/zones">{t('nav.adminZones')}</Link>,
    canOnPlatform(access, 'reference-data:read') && <Link className={TILE} key="c" to="/admin/unit-categories">{t('nav.adminCategories')}</Link>,
    canOnPlatform(access, 'text:read') && <Link className={TILE} key="t" to="/admin/texts">{t('nav.adminTexts')}</Link>,
    canOnPlatform(access, 'support:access') && <Link className={TILE} key="s" to="/admin/support">{t('nav.adminSupport')}</Link>,
    canOnPlatform(access, 'ticket-workflow:read') && <Link className={TILE} key="w" to="/admin/workflows">{t('nav.adminWorkflows')}</Link>,
  ].filter(Boolean)
  if (links.length === 0) return null
  return (
    <Card title={t('nav.admin')}>
      <div className="flex flex-wrap gap-2">{links}</div>
    </Card>
  )
}
