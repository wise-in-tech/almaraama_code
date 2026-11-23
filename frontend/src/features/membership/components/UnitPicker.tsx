import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, unwrap, type components } from '@/shared/api'
import { TextField } from '@/shared/ui'

type OccupancyRole = components['schemas']['OccupancyRole']

/** A unit chosen for a person, with their role on it (E2 to E4). */
export type ChosenUnit = { unitId: string; reference: string; role: OccupancyRole; primaryTenant: boolean }

/**
 * Picks the units given to a person (A7, E1 to E5): a search by reference among the site's units, each chosen unit
 * with owner or tenant (several apartments, parkings and storage rooms). The server checks E3 and E4.
 */
export function UnitPicker({
  siteId,
  chosen,
  onChange,
}: {
  siteId: string
  chosen: ChosenUnit[]
  onChange: (units: ChosenUnit[]) => void
}) {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const units = useQuery({
    queryKey: ['sites', siteId, 'units', 'picker', search],
    queryFn: () =>
      unwrap(
        api.GET('/v1/sites/{siteId}/units', {
          params: { path: { siteId }, query: { size: 50, ...(search.trim() ? { q: search.trim() } : {}) } },
        }),
      ),
  })
  const toggle = (unitId: string, reference: string) =>
    onChange(
      chosen.some((u) => u.unitId === unitId)
        ? chosen.filter((u) => u.unitId !== unitId)
        : [...chosen, { unitId, reference, role: 'OWNER', primaryTenant: false }],
    )
  const setRole = (unitId: string, role: OccupancyRole) =>
    onChange(chosen.map((u) => (u.unitId === unitId ? { ...u, role, primaryTenant: false } : u)))

  return (
    <div className="grid gap-2">
      <TextField id={`unit-search-${siteId}`} label={t('units.search')} dir="ltr" value={search}
        onChange={(event) => setSearch(event.target.value)} />
      <ul className="grid max-h-64 gap-1 overflow-y-auto rounded-lg border border-line p-2">
        {units.data?.items.map((unit) => {
          const selected = chosen.find((u) => u.unitId === unit.id)
          return (
            <li key={unit.id} className="flex flex-wrap items-center gap-2">
              <label className="flex min-h-10 flex-1 items-center gap-2">
                <input type="checkbox" className="size-5 accent-brand" checked={selected !== undefined}
                  onChange={() => toggle(unit.id, unit.reference)} />
                <span>{`${unit.reference} · ${t(`unitType.${unit.type}`)}`}</span>
              </label>
              {selected && (
                <select aria-label={t('units.role')} value={selected.role} className="min-h-10 rounded-lg border border-line px-2"
                  onChange={(event) => setRole(unit.id, event.target.value as OccupancyRole)}>
                  <option value="OWNER">{t('occupancyRole.OWNER')}</option>
                  <option value="TENANT">{t('occupancyRole.TENANT')}</option>
                </select>
              )}
            </li>
          )
        })}
      </ul>
      <p className="text-sm text-muted">{t('units.chosen', { count: chosen.length })}</p>
    </div>
  )
}
