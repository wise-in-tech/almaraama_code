import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Button, Card, CheckboxField, ErrorMessage, Loading, PageHeader, SelectField, Table, TextField } from '@/shared/ui'

type OrganizationWorkflow = components['schemas']['OrganizationWorkflow']

/**
 * The ticket circuits (C12, ADR-036): every circuit with its versions; the steps and passages of a version; the
 * publication of a draft loaded by the architect (checked by the server). No editor in P1.
 */
export function WorkflowsAdminPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [shown, setShown] = useState<{ workflowId: string; number: number } | null>(null)
  const workflows = useQuery({ queryKey: ['admin', 'workflows'], queryFn: () => unwrap(api.GET('/v1/admin/workflows')) })
  const publish = useMutation({
    mutationFn: (v: { workflowId: string; number: number }) =>
      unwrap(api.POST('/v1/admin/workflows/{workflowId}/versions/{versionNumber}/publish', {
        params: { path: { workflowId: v.workflowId, versionNumber: v.number } },
      })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'workflows'] }),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('circuits.title')} intro={t('circuits.intro')} />
      {workflows.isPending && <Loading />}
      <ErrorMessage message={workflows.error || publish.error ? problemMessage(workflows.error ?? publish.error, t) : null} />
      {workflows.data?.map((w) => (
        <Card key={w.id} title={`${w.name} (${w.code})`}>
          <p className="text-sm text-muted">{t('circuits.used', { count: w.organizationsUsing ?? 0 })}</p>
          <ul className="grid gap-2">
            {w.versions.map((v) => (
              <li key={v.number} className="flex flex-wrap items-center gap-2">
                <span>{t('circuits.version', { number: v.number })}</span>
                <Badge tone={v.status === 'PUBLISHED' ? 'good' : v.status === 'DRAFT' ? 'warn' : 'neutral'}>{t(`circuits.status.${v.status}`)}</Badge>
                <Button onClick={() => setShown({ workflowId: w.id, number: v.number })}>{t('circuits.show')}</Button>
                {v.status === 'DRAFT' && (
                  <Button tone="primary" busy={publish.isPending} onClick={() => publish.mutate({ workflowId: w.id, number: v.number })}>
                    {t('circuits.publish')}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </Card>
      ))}
      {shown && <VersionDetail workflowId={shown.workflowId} number={shown.number} />}
    </section>
  )
}

/** The steps and passages of one version. */
function VersionDetail({ workflowId, number }: { workflowId: string; number: number }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const detail = useQuery({
    queryKey: ['admin', 'workflows', workflowId, number],
    queryFn: () => unwrap(api.GET('/v1/admin/workflows/{workflowId}/versions/{versionNumber}', {
      params: { path: { workflowId, versionNumber: number } },
    })),
  })
  if (!detail.data) return <Loading />
  return (
    <Card title={`${detail.data.name} · ${t('circuits.version', { number })}`}>
      <Table headers={[t('circuits.step'), t('circuits.category')]}
        rows={detail.data.nodes.map((n) => [`${localize(n.label)} (${n.code})`, n.category])} />
      <Table headers={[t('circuits.passage'), t('circuits.fromTo'), t('circuits.permission'), t('circuits.guards')]}
        rows={detail.data.transitions.map((tr) => [tr.code, `${tr.from} → ${tr.to}`, tr.permission ?? '—', tr.guards.map((g) => g.code).join(', ')])} />
    </Card>
  )
}

/**
 * The circuit of one client and its options (H5, criterion 19): the circuit for new tickets; automatic closing after a
 * number of days in "resolved", validation by the requester, the client's labels of the steps, which apply at once.
 */
export function OrganizationWorkflowPage() {
  const { t } = useTranslation()
  const { organizationId } = useParams({ strict: false }) as { organizationId: string }
  const current = useQuery({
    queryKey: ['admin', 'organizations', organizationId, 'workflow'],
    queryFn: () => unwrap(api.GET('/v1/admin/organizations/{organizationId}/workflow', { params: { path: { organizationId } } })),
  })
  const workflows = useQuery({ queryKey: ['admin', 'workflows'], queryFn: () => unwrap(api.GET('/v1/admin/workflows')) })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('circuits.clientTitle')} intro={t('circuits.clientIntro')} />
      <Link className="w-fit text-sm underline" to="/admin/organizations">{t('nav.adminOrganizations')}</Link>
      {current.isPending && <Loading />}
      <ErrorMessage message={current.error ? problemMessage(current.error, t) : null} />
      {current.data && workflows.data && (
        <ClientForm organizationId={organizationId} current={current.data}
          circuits={workflows.data.filter((w) => w.versions.some((v) => v.status === 'PUBLISHED'))} />
      )}
    </section>
  )
}

/** The form of a client's circuit and options. */
function ClientForm({ organizationId, current, circuits }: {
  organizationId: string
  current: OrganizationWorkflow
  circuits: { id: string; name: string; code: string }[]
}) {
  const { t } = useTranslation()
  const { localize, language } = useLocalizedText()
  const queryClient = useQueryClient()
  const [workflowId, setWorkflowId] = useState(current.workflow.id)
  const [autoClose, setAutoClose] = useState(current.settings.autoCloseAfterDays !== undefined)
  const [days, setDays] = useState(String(current.settings.autoCloseAfterDays ?? 5))
  const [validation, setValidation] = useState(current.settings.requesterValidation)
  const [labels, setLabels] = useState<Record<string, string>>(() =>
    Object.fromEntries(Object.entries(current.settings.labels).map(([code, text]) => [code, text[language] ?? ''])))
  const save = useMutation({
    mutationFn: () =>
      unwrap(api.PUT('/v1/admin/organizations/{organizationId}/workflow', {
        params: { path: { organizationId } },
        body: {
          workflowId,
          ...(current.chosen && current.version !== undefined ? { version: current.version } : {}),
          settings: {
            requesterValidation: validation,
            ...(autoClose ? { autoCloseAfterDays: Number(days) } : {}),
            labels: Object.fromEntries(Object.entries(labels).filter(([, text]) => text.trim() !== '')
              .map(([code, text]) => [code, { ...(current.settings.labels[code] ?? {}), [language]: text.trim() }])),
          },
        },
      })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin', 'organizations', organizationId, 'workflow'] }),
  })
  return (
    <Card>
      <SelectField id="client-circuit" label={t('circuits.circuit')} value={workflowId} onChange={(e) => setWorkflowId(e.target.value)}
        options={circuits.map((w) => ({ value: w.id, label: `${w.name} (${w.code})` }))} />
      <CheckboxField id="client-auto-close" label={t('circuits.autoClose')} checked={autoClose} onChange={(e) => setAutoClose(e.target.checked)} />
      {autoClose && (
        <TextField id="client-days" label={t('circuits.days')} type="number" min={1} max={90} inputMode="numeric" value={days}
          onChange={(e) => setDays(e.target.value)} />
      )}
      <CheckboxField id="client-validation" label={t('circuits.requesterValidation')} checked={validation} onChange={(e) => setValidation(e.target.checked)} />
      <fieldset className="grid gap-2">
        <legend className="text-sm font-medium">{t('circuits.labels', { language: t(`language.${language}`) })}</legend>
        {current.nodes.map((node) => (
          <TextField key={node.code} id={`label-${node.code}`} label={localize(node.label)} maxLength={100} placeholder={localize(node.label)}
            value={labels[node.code] ?? ''} onChange={(e) => setLabels((v) => ({ ...v, [node.code]: e.target.value }))} />
        ))}
      </fieldset>
      <ErrorMessage message={save.error ? problemMessage(save.error, t) : null} />
      <Button tone="primary" busy={save.isPending} onClick={() => save.mutate()}>{t('common.save')}</Button>
    </Card>
  )
}
