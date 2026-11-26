import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Button, Card, CheckboxField, ErrorMessage, Loading, PageHeader, SelectField } from '@/shared/ui'
import type { IssueTypeConfig } from '../api/queries'

/**
 * Where tickets of each type go on this residence, and who sees them (H4, H8, criterion 9): the managers or one to
 * five designated people (who must be able to act on tickets here), common or private, offered or not. New tickets
 * only; every change is audited.
 */
export function IssueTypeConfigsPage() {
  const { t } = useTranslation()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const configs = useQuery({
    queryKey: ['sites', siteId, 'issue-type-configs'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/issue-type-configs', { params: { path: { siteId } } })),
  })
  const technicians = useQuery({
    queryKey: ['sites', siteId, 'eligible-technicians'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/eligible-technicians', { params: { path: { siteId } } })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('routing.title')} intro={t('routing.intro')} />
      {configs.isPending && <Loading />}
      <ErrorMessage message={configs.error ? problemMessage(configs.error, t) : null} />
      {configs.data?.map((config) => (
        <ConfigCard key={config.issueType.id} siteId={siteId} config={config} people={technicians.data ?? []} />
      ))}
    </section>
  )
}

/** The setting of one type. */
function ConfigCard({ siteId, config, people }: {
  siteId: string
  config: IssueTypeConfig
  people: { id: string; firstName: string; lastName: string }[]
}) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const [routing, setRouting] = useState(config.routing)
  const [chosen, setChosen] = useState<string[]>(config.technicians.map((p) => p.id))
  const [visibility, setVisibility] = useState(config.visibility)
  const [active, setActive] = useState(config.active)
  const save = useMutation({
    mutationFn: () =>
      unwrap(api.PUT('/v1/sites/{siteId}/issue-type-configs/{issueTypeId}', {
        params: { path: { siteId, issueTypeId: config.issueType.id } },
        body: { routing, technicianIds: routing === 'TECHNICIANS' ? chosen : [], visibility, active,
          ...(config.version !== undefined ? { version: config.version } : {}) },
      })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'issue-type-configs'] }),
  })
  return (
    <Card title={`${localize(config.categoryName)} · ${localize(config.issueType.name)}`}>
      <div className="grid gap-3 sm:grid-cols-3">
        <SelectField id={`routing-${config.issueType.id}`} label={t('routing.destination')} value={routing}
          onChange={(e) => setRouting(e.target.value as typeof routing)}
          options={(['SITE_MANAGER', 'TECHNICIANS'] as const).map((value) => ({ value, label: t(`routing.${value}`) }))} />
        <SelectField id={`visibility-${config.issueType.id}`} label={t('routing.visibility')} value={visibility}
          onChange={(e) => setVisibility(e.target.value as typeof visibility)}
          options={(['COMMON', 'PRIVATE'] as const).map((value) => ({ value, label: t(`visibility.${value}`) }))} />
        <CheckboxField id={`active-${config.issueType.id}`} label={t('routing.offered')} checked={active} onChange={(e) => setActive(e.target.checked)} />
      </div>
      {routing === 'TECHNICIANS' && (
        <fieldset className="grid gap-1">
          <legend className="text-sm font-medium">{t('routing.people')}</legend>
          {people.map((person) => (
            <CheckboxField key={person.id} id={`tech-${config.issueType.id}-${person.id}`} label={`${person.firstName} ${person.lastName}`}
              checked={chosen.includes(person.id)}
              onChange={(e) => setChosen((list) => e.target.checked ? [...list, person.id].slice(0, 5) : list.filter((id) => id !== person.id))} />
          ))}
        </fieldset>
      )}
      <ErrorMessage message={save.error ? problemMessage(save.error, t) : null} />
      <Button tone="primary" busy={save.isPending} disabled={routing === 'TECHNICIANS' && chosen.length === 0} onClick={() => save.mutate()}>
        {t('common.save')}
      </Button>
    </Card>
  )
}
