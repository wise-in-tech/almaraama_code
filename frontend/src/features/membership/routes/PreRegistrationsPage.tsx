import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, fileBody, problemMessage, unwrap, type components } from '@/shared/api'
import { downloadBlob } from '@/shared/download'
import { DEFAULT_PHONE_COUNTRY, PhoneField, toE164 } from '@/shared/phone'
import {
  Badge, Button, Card, CheckboxField, EmptyState, ErrorMessage, ImportReportView, Loading, Notice, PageHeader,
  SelectField, Table, TextField,
} from '@/shared/ui'
import type { PreRegistration } from '../api/queries'
import { UnitPicker, type ChosenUnit } from '../components/UnitPicker'
import { downloadKeys, type IssuedKey } from '../lib/keys'

type Language = components['schemas']['Language']
type ImportReport = components['schemas']['ResidentImportReport']

type PersonForm = { firstName: string; lastName: string; email: string; phoneCountry: string; phone: string; language: Language; days: number }

/**
 * Pre-registrations of a residence (D3, criterion 6, ADR-034): expected people with their planned units and a personal
 * key, shown once at creation or renewal; cancelled ones are kept (M2). Also the resident import from Excel
 * (ADR-027), whose keys are shown once and can be printed or downloaded as CSV.
 */
export function PreRegistrationsPage() {
  const { t } = useTranslation()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const [issued, setIssued] = useState<IssuedKey[]>([])
  const list = useQuery({
    queryKey: ['sites', siteId, 'pre-registrations'],
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/pre-registrations', { params: { path: { siteId }, query: { size: 100 } } })),
  })

  return (
    <section className="grid gap-4">
      <PageHeader title={t('pre.title')} intro={t('pre.intro')} />
      <KeysPanel keys={issued} onClose={() => setIssued([])} />
      <CreatePreRegistration siteId={siteId} onIssued={(key) => setIssued([key])} />
      <ResidentImport siteId={siteId} onIssued={setIssued} />
      <Card title={t('pre.listTitle')}>
        {list.isPending && <Loading />}
        {list.isError && <ErrorMessage message={problemMessage(list.error, t)} />}
        {list.data?.items.length === 0 && <EmptyState message={t('pre.none')} />}
        {list.data && list.data.items.length > 0 && (
          <Table
            headers={[t('person.name'), t('login.email'), t('pre.units'), t('pre.status'), '']}
            rows={list.data.items.map((pre) => [
              `${pre.firstName} ${pre.lastName}`,
              <span dir="ltr">{pre.email}</span>,
              pre.plannedOccupancies.map((o) => o.unitReference).join(', '),
              <Badge tone={pre.status === 'USED' ? 'good' : pre.status === 'PENDING' ? 'neutral' : 'warn'}>
                {t(`preStatus.${pre.status}`)}
              </Badge>,
              <PreRegistrationActions siteId={siteId} pre={pre} onIssued={(key) => setIssued([key])} />,
            ])}
          />
        )}
      </Card>
    </section>
  )
}

/** The keys just issued, shown this once, with print and CSV download. */
function KeysPanel({ keys, onClose }: { keys: IssuedKey[]; onClose: () => void }) {
  const { t } = useTranslation()
  if (keys.length === 0) return null
  const headers = [t('person.lastName'), t('person.firstName'), t('login.email'), t('pre.key'), t('pre.expires')]
  return (
    <Card title={t('pre.keysTitle')} className="border-brand">
      <Notice message={t('pre.keysOnce')} />
      <Table headers={headers} rows={keys.map((k) => [k.lastName, k.firstName, <span dir="ltr">{k.email}</span>,
        <span dir="ltr" className="font-mono text-lg">{k.key}</span>, k.expiresAt.slice(0, 10)])} />
      <div className="flex flex-wrap gap-2 print:hidden">
        <Button onClick={() => window.print()}>{t('pre.printKeys')}</Button>
        <Button onClick={() => downloadKeys(keys, headers, 'keys.csv')}>{t('pre.downloadKeys')}</Button>
        <Button onClick={onClose}>{t('pre.keysDone')}</Button>
      </div>
    </Card>
  )
}

/** Renewal of the key (a lost key) and cancellation of a pending pre-registration. */
function PreRegistrationActions({ siteId, pre, onIssued }: { siteId: string; pre: PreRegistration; onIssued: (key: IssuedKey) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'pre-registrations'] })
  const renew = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/pre-registrations/{preRegistrationId}/renew-key', {
      params: { path: { preRegistrationId: pre.id } }, body: { keyValidDays: 30, version: pre.version } })),
    onSuccess: (data) => {
      onIssued({ ...data.preRegistration, key: data.key })
      void refresh()
    },
  })
  const cancel = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/pre-registrations/{preRegistrationId}/cancel', {
      params: { path: { preRegistrationId: pre.id } }, body: {} })),
    onSuccess: refresh,
  })
  if (pre.status !== 'PENDING' && pre.status !== 'EXPIRED') return null
  return (
    <div className="flex flex-wrap gap-1">
      <Button busy={renew.isPending} onClick={() => renew.mutate()}>{t('pre.renew')}</Button>
      <Button tone="danger" busy={cancel.isPending} onClick={() => {
        if (window.confirm(t('pre.cancelConfirm'))) cancel.mutate()
      }}>{t('pre.cancel')}</Button>
      <ErrorMessage message={renew.error || cancel.error ? problemMessage(renew.error ?? cancel.error, t) : null} />
    </div>
  )
}

/** The form of one pre-registration: the person, their planned units, the validity of the key. */
function CreatePreRegistration({ siteId, onIssued }: { siteId: string; onIssued: (key: IssuedKey) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [units, setUnits] = useState<ChosenUnit[]>([])
  const { register, handleSubmit, reset, setError, formState } = useForm<PersonForm>({
    defaultValues: { firstName: '', lastName: '', email: '', phoneCountry: DEFAULT_PHONE_COUNTRY, phone: '', language: 'AR', days: 30 },
  })
  const create = useMutation({
    mutationFn: (body: components['schemas']['PreRegistrationRequest']) =>
      unwrap(api.POST('/v1/sites/{siteId}/pre-registrations', { params: { path: { siteId } }, body })),
    onSuccess: (data) => {
      onIssued({ ...data.preRegistration, key: data.key })
      reset()
      setUnits([])
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'pre-registrations'] })
    },
  })
  const onSubmit = handleSubmit((form) => {
    const phone = toE164(form.phoneCountry, form.phone)
    if (!phone) {
      setError('phone', { message: t('validation.phoneInvalid') })
      return
    }
    create.mutate({
      person: { firstName: form.firstName.trim(), lastName: form.lastName.trim(), email: form.email.trim(), phone, language: form.language },
      plannedOccupancies: units.map((u) => ({ unitId: u.unitId, role: u.role, primaryTenant: u.primaryTenant })),
      keyValidDays: Number(form.days),
    })
  })
  const required = { required: t('validation.required') }
  return (
    <Card title={t('pre.createTitle')}>
      <form noValidate onSubmit={onSubmit} className="grid gap-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField id="pre-first" label={t('person.firstName')} error={formState.errors.firstName?.message} {...register('firstName', required)} />
          <TextField id="pre-last" label={t('person.lastName')} error={formState.errors.lastName?.message} {...register('lastName', required)} />
          <TextField id="pre-email" label={t('login.email')} type="email" dir="ltr" error={formState.errors.email?.message} {...register('email', required)} />
          <SelectField id="pre-language" label={t('person.language')}
            options={(['AR', 'EN', 'FR'] as const).map((value) => ({ value, label: t(`language.${value.toLowerCase()}`) }))}
            {...register('language')} />
        </div>
        <PhoneField id="pre-phone" label={t('person.phone')} countryLabel={t('login.phoneCountry')}
          error={formState.errors.phone?.message} country={register('phoneCountry')} number={register('phone', required)} />
        <UnitPicker siteId={siteId} chosen={units} onChange={setUnits} />
        <TextField id="pre-days" label={t('pre.days')} type="number" min={1} max={90} dir="ltr" {...register('days')} />
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
        <Button type="submit" tone="primary" busy={create.isPending} disabled={units.length === 0}>
          {t('pre.create')}
        </Button>
      </form>
    </Card>
  )
}

/** The resident import (ADR-027): template, file, check only or import, report by row and column. */
function ResidentImport({ siteId, onIssued }: { siteId: string; onIssued: (keys: IssuedKey[]) => void }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [dryRun, setDryRun] = useState(true)
  const [report, setReport] = useState<ImportReport | null>(null)
  const template = useMutation({
    mutationFn: async () => {
      const { data, error } = await api.GET('/v1/residents/import-template', {
        params: { query: { language: i18n.language.toUpperCase() as Language } },
        parseAs: 'blob',
      })
      if (error) throw error
      downloadBlob(data as Blob, 'residents.xlsx')
    },
  })
  const upload = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/v1/sites/{siteId}/residents/import', {
        params: { path: { siteId }, query: { dryRun } },
        ...fileBody(file as File),
      })),
    onSuccess: (data) => {
      setReport(data)
      if (data.outcome === 'IMPORTED') {
        onIssued(data.keys)
        void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'pre-registrations'] })
      }
    },
  })
  return (
    <Card title={t('import.residentsTitle')}>
      <p className="text-sm text-muted">{t('import.residentsIntro')}</p>
      <div className="flex flex-wrap gap-2">
        <Button busy={template.isPending} onClick={() => template.mutate()}>{t('import.template')}</Button>
      </div>
      <input type="file" aria-label={t('import.file')} accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
      <CheckboxField id={`dry-run-${siteId}`} label={t('import.dryRun')} checked={dryRun} onChange={(event) => setDryRun(event.target.checked)} />
      <Button tone="primary" busy={upload.isPending} disabled={!file} onClick={() => upload.mutate()}>{t('import.send')}</Button>
      <ErrorMessage message={upload.error || template.error ? problemMessage(upload.error ?? template.error, t) : null} />
      {report && <ImportReportView report={report} />}
    </Card>
  )
}
