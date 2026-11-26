import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { DEFAULT_PHONE_COUNTRY, PhoneField, toE164 } from '@/shared/phone'
import { Badge, Button, Card, ErrorMessage, Loading, Notice, PageHeader, SelectField, Table, TextField } from '@/shared/ui'

type AdminOrganization = components['schemas']['AdminOrganization']
type OrganizationType = components['schemas']['OrganizationType']
type OwnerForm = { firstName: string; lastName: string; email: string; phoneCountry: string; phone: string }
type CreateForm = OwnerForm & { name: string; type: OrganizationType }

/**
 * Back-office of the client organizations (C8, C12, ADR-035): every organization with its sites and promoteurs; our
 * team onboards one with its first promoteur, invites one more promoteur, deactivates or reactivates it with a reason,
 * and opens a support session (reason, one hour, every action logged).
 */
export function OrganizationsAdminPage() {
  const { t } = useTranslation()
  const [q, setQ] = useState('')
  const organizations = useQuery({
    queryKey: ['admin', 'organizations', q],
    queryFn: () => unwrap(api.GET('/v1/admin/organizations', { params: { query: { size: 100, ...(q.trim() ? { q: q.trim() } : {}) } } })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('adminOrganizations.title')} intro={t('adminOrganizations.intro')} />
      <CreateOrganization />
      <TextField id="admin-org-search" label={t('common.search')} value={q} onChange={(e) => setQ(e.target.value)} />
      {organizations.isPending && <Loading />}
      {organizations.isError && <ErrorMessage message={problemMessage(organizations.error, t)} />}
      {organizations.data?.items.map((organization) => <OrganizationRow key={organization.id} organization={organization} />)}
    </section>
  )
}

/** One organization with its actions. */
function OrganizationRow({ organization }: { organization: AdminOrganization }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'organizations'] })
  const toggle = useMutation({
    mutationFn: () => {
      const options = { params: { path: { organizationId: organization.id } }, body: { reason: reason.trim() } }
      return unwrap(organization.active
        ? api.POST('/v1/admin/organizations/{organizationId}/deactivate', options)
        : api.POST('/v1/admin/organizations/{organizationId}/reactivate', options))
    },
    onSuccess: () => {
      setReason('')
      void refresh()
    },
  })
  const support = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/admin/support-sessions', { body: { organizationId: organization.id, reason: reason.trim() } })),
    onSuccess: () => {
      setNotice(t('support.opened'))
      void queryClient.invalidateQueries({ queryKey: ['me'] })
    },
  })
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-semibold">{organization.name}</p>
          <p className="text-sm text-muted">
            {t('adminOrganizations.counts', { sites: organization.siteCount, owners: organization.ownerCount })}
          </p>
        </div>
        <Badge tone={organization.active ? 'good' : 'warn'}>{organization.active ? t('common.active') : t('common.inactive')}</Badge>
      </div>
      <TextField id={`reason-${organization.id}`} label={t('adminOrganizations.reason')} value={reason} maxLength={500}
        hint={t('adminOrganizations.reasonHint')} onChange={(e) => setReason(e.target.value)} />
      <div className="flex flex-wrap gap-2">
        <Button tone={organization.active ? 'danger' : 'secondary'} busy={toggle.isPending} disabled={reason.trim().length < 3}
          onClick={() => toggle.mutate()}>
          {organization.active ? t('adminOrganizations.deactivate') : t('adminOrganizations.reactivate')}
        </Button>
        {organization.active && (
          <Button busy={support.isPending} disabled={reason.trim().length < 10} onClick={() => support.mutate()}>
            {t('support.open')}
          </Button>
        )}
        <Link className="rounded-lg border border-line px-4 py-2 text-sm font-semibold" to="/admin/organizations/$organizationId/workflow"
          params={{ organizationId: organization.id }}>{t('nav.adminWorkflow')}</Link>
      </div>
      <Notice message={notice} />
      <ErrorMessage message={toggle.error || support.error ? problemMessage(toggle.error ?? support.error, t) : null} />
      <InviteOwner organizationId={organization.id} />
    </Card>
  )
}

/** The fields of a promoteur: names, e-mail, phone. */
function OwnerFields({ prefix, form }: { prefix: string; form: ReturnType<typeof useForm<OwnerForm>> }) {
  const { t } = useTranslation()
  const required = { required: t('validation.required') }
  const errors = form.formState.errors
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <TextField id={`${prefix}-first`} label={t('person.firstName')} error={errors.firstName?.message} {...form.register('firstName', required)} />
      <TextField id={`${prefix}-last`} label={t('person.lastName')} error={errors.lastName?.message} {...form.register('lastName', required)} />
      <TextField id={`${prefix}-email`} label={t('login.email')} type="email" dir="ltr" error={errors.email?.message} {...form.register('email', required)} />
      <PhoneField id={`${prefix}-phone`} label={t('person.phone')} countryLabel={t('login.phoneCountry')} error={errors.phone?.message}
        country={form.register('phoneCountry')} number={form.register('phone', required)} />
    </div>
  )
}

/** C8: our team invites one more promoteur (for example when the first one left). */
function InviteOwner({ organizationId }: { organizationId: string }) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const form = useForm<OwnerForm>({ defaultValues: { firstName: '', lastName: '', email: '', phoneCountry: DEFAULT_PHONE_COUNTRY, phone: '' } })
  const invite = useMutation({
    mutationFn: (body: components['schemas']['PersonInput']) =>
      unwrap(api.POST('/v1/admin/organizations/{organizationId}/owner-invitations', { params: { path: { organizationId } }, body })),
    onSuccess: () => {
      form.reset()
      setOpen(false)
    },
  })
  if (!open) return <Button onClick={() => setOpen(true)}>{t('adminOrganizations.inviteOwner')}</Button>
  return (
    <form noValidate className="grid gap-3" onSubmit={form.handleSubmit((values) => {
      const phone = toE164(values.phoneCountry, values.phone)
      if (!phone) return form.setError('phone', { message: t('validation.phoneInvalid') })
      invite.mutate({ firstName: values.firstName, lastName: values.lastName, email: values.email, phone,
        language: i18n.language.toUpperCase() as components['schemas']['Language'] })
    })}>
      <OwnerFields prefix={`owner-${organizationId}`} form={form} />
      <ErrorMessage message={invite.error ? problemMessage(invite.error, t) : null} />
      <Button type="submit" tone="primary" busy={invite.isPending}>{t('adminOrganizations.sendInvitation')}</Button>
    </form>
  )
}

/** C8, C12: onboards a client organization with its first promoteur, who receives a "set my password" link. */
function CreateOrganization() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const form = useForm<CreateForm>({
    defaultValues: { name: '', type: 'PROPERTY_DEVELOPER', firstName: '', lastName: '', email: '', phoneCountry: DEFAULT_PHONE_COUNTRY, phone: '' },
  })
  const create = useMutation({
    mutationFn: (body: components['schemas']['CreateOrganizationRequest']) => unwrap(api.POST('/v1/organizations', { body })),
    onSuccess: () => {
      form.reset()
      void queryClient.invalidateQueries({ queryKey: ['admin', 'organizations'] })
    },
  })
  return (
    <Card title={t('adminOrganizations.createTitle')}>
      <form noValidate className="grid gap-3" onSubmit={form.handleSubmit((values) => {
        const phone = toE164(values.phoneCountry, values.phone)
        if (!phone) return form.setError('phone', { message: t('validation.phoneInvalid') })
        create.mutate({ type: values.type, name: values.name.trim(), countryCode: 'DZ',
          owner: { firstName: values.firstName, lastName: values.lastName, email: values.email, phone,
            language: i18n.language.toUpperCase() as components['schemas']['Language'] } })
      })}>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField id="org-name" label={t('adminOrganizations.name')} error={form.formState.errors.name?.message}
            {...form.register('name', { required: t('validation.required') })} />
          <SelectField id="org-type" label={t('adminOrganizations.type')} {...form.register('type')}
            options={(['PROPERTY_DEVELOPER', 'RESIDENCE'] as const).map((value) => ({ value, label: t(`organizationType.${value}`) }))} />
        </div>
        <OwnerFields prefix="org-owner" form={form as unknown as ReturnType<typeof useForm<OwnerForm>>} />
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
        <Button type="submit" tone="primary" busy={create.isPending}>{t('adminOrganizations.create')}</Button>
      </form>
    </Card>
  )
}

/** The support sessions of every administrator, newest first; the open ones can be ended by their administrator. */
export function SupportSessionsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const sessions = useQuery({
    queryKey: ['admin', 'support-sessions'],
    queryFn: () => unwrap(api.GET('/v1/admin/support-sessions', { params: { query: { size: 100 } } })),
  })
  const end = useMutation({
    mutationFn: (supportSessionId: string) => unwrap(api.POST('/v1/admin/support-sessions/{supportSessionId}/end', { params: { path: { supportSessionId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'support-sessions'] }).then(() => queryClient.invalidateQueries({ queryKey: ['me'] })),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('support.title')} intro={t('support.intro')} />
      {sessions.isPending && <Loading />}
      <ErrorMessage message={sessions.error || end.error ? problemMessage(sessions.error ?? end.error, t) : null} />
      {sessions.data && (
        <Table
          headers={[t('support.organization'), t('support.administrator'), t('support.reason'), t('support.status'), '']}
          rows={sessions.data.items.map((s) => [
            s.organization.name,
            `${s.administrator.firstName} ${s.administrator.lastName}`,
            s.reason,
            t(`supportStatus.${s.status}`),
            s.status === 'OPEN' ? <Button onClick={() => end.mutate(s.id)}>{t('support.end')}</Button> : null,
          ])}
        />
      )}
    </section>
  )
}
