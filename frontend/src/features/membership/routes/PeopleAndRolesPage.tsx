import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap, type components } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { DEFAULT_PHONE_COUNTRY, PhoneField, toE164 } from '@/shared/phone'
import { Badge, Button, Card, ErrorMessage, Loading, PageHeader, SelectField, Table, TextField } from '@/shared/ui'

type PermissionCode = components['schemas']['PermissionCode']
type OrganizationRole = components['schemas']['OrganizationRole']

/**
 * People and roles of an organization (C2, C8, C9, C11, criterion 16, ADR-026): the people with their roles, the
 * invitation of a manager on a residence, and the permissions of each role, changed by the promoteur and stored as
 * differences from the defaults; "back to defaults" cancels the differences. Non-adjustable permissions are never
 * offered; sensitive ones are flagged. Every change applies at the next request and is logged.
 */
export function PeopleAndRolesPage() {
  const { t } = useTranslation()
  const { organizationId } = useParams({ strict: false }) as { organizationId: string }
  return (
    <section className="grid gap-4">
      <PageHeader title={t('people.title')} intro={t('people.intro')} />
      <InviteManager organizationId={organizationId} />
      <PeopleList organizationId={organizationId} />
      <RolesEditor organizationId={organizationId} />
    </section>
  )
}

/** People with a role or an access in the organization. */
function PeopleList({ organizationId }: { organizationId: string }) {
  const { t } = useTranslation()
  const people = useQuery({
    queryKey: ['organizations', organizationId, 'people'],
    queryFn: () => unwrap(api.GET('/v1/organizations/{organizationId}/people', { params: { path: { organizationId }, query: { size: 100 } } })),
  })
  if (people.isPending) return <Loading />
  if (people.isError) return <ErrorMessage message={problemMessage(people.error, t)} />
  return (
    <Table
      headers={[t('person.name'), t('login.email'), t('people.roles'), t('people.account')]}
      rows={people.data.items.map((p) => [
        `${p.firstName} ${p.lastName}`,
        <span dir="ltr">{p.email}</span>,
        p.roles.map((r) => t(`roles.${r.roleCode}`, { defaultValue: r.roleCode })).join(', '),
        <Badge tone={p.accountStatus === 'ACTIVE' ? 'good' : 'warn'}>{t(`accountStatus.${p.accountStatus}`)}</Badge>,
      ])}
    />
  )
}

type ManagerForm = { firstName: string; lastName: string; email: string; phoneCountry: string; phone: string; siteId: string }

/** C2: the promoteur gives the manager role on one of their residences; the person receives a password link. */
function InviteManager({ organizationId }: { organizationId: string }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const sites = useQuery({
    queryKey: ['organizations', organizationId, 'sites', 'all'],
    queryFn: () => unwrap(api.GET('/v1/organizations/{organizationId}/sites', { params: { path: { organizationId }, query: { size: 100 } } })),
  })
  const form = useForm<ManagerForm>({ defaultValues: { firstName: '', lastName: '', email: '', phoneCountry: DEFAULT_PHONE_COUNTRY, phone: '', siteId: '' } })
  const invite = useMutation({
    mutationFn: (body: components['schemas']['OrganizationInvitationRequest']) =>
      unwrap(api.POST('/v1/organizations/{organizationId}/invitations', { params: { path: { organizationId } }, body })),
    onSuccess: () => {
      form.reset()
      void queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'people'] })
    },
  })
  const required = { required: t('validation.required') }
  const errors = form.formState.errors
  return (
    <Card title={t('people.inviteManager')}>
      <form noValidate className="grid gap-3" onSubmit={form.handleSubmit((values) => {
        const phone = toE164(values.phoneCountry, values.phone)
        if (!phone) return form.setError('phone', { message: t('validation.phoneInvalid') })
        invite.mutate({
          person: { firstName: values.firstName, lastName: values.lastName, email: values.email, phone,
            language: i18n.language.toUpperCase() as components['schemas']['Language'] },
          roles: [{ roleCode: 'SITE_MANAGER', scope: { type: 'SITE', id: values.siteId } }],
        })
      })}>
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField id="manager-first" label={t('person.firstName')} error={errors.firstName?.message} {...form.register('firstName', required)} />
          <TextField id="manager-last" label={t('person.lastName')} error={errors.lastName?.message} {...form.register('lastName', required)} />
          <TextField id="manager-email" label={t('login.email')} type="email" dir="ltr" error={errors.email?.message} {...form.register('email', required)} />
          <SelectField id="manager-site" label={t('people.site')} {...form.register('siteId', required)}
            options={[{ value: '', label: '—' }, ...(sites.data?.items ?? []).map((s) => ({ value: s.id, label: s.name }))]} />
        </div>
        <PhoneField id="manager-phone" label={t('person.phone')} countryLabel={t('login.phoneCountry')} error={errors.phone?.message}
          country={form.register('phoneCountry')} number={form.register('phone', required)} />
        <ErrorMessage message={invite.error ? problemMessage(invite.error, t) : null} />
        <Button type="submit" tone="primary" busy={invite.isPending}>{t('people.invite')}</Button>
      </form>
    </Card>
  )
}

/** The permissions of each role of the organization, as checkboxes (C9, C11, criterion 16). */
function RolesEditor({ organizationId }: { organizationId: string }) {
  const { t } = useTranslation()
  const roles = useQuery({
    queryKey: ['organizations', organizationId, 'roles'],
    queryFn: () => unwrap(api.GET('/v1/organizations/{organizationId}/roles', { params: { path: { organizationId } } })),
  })
  const catalogue = useQuery({ queryKey: ['permissions'], queryFn: () => unwrap(api.GET('/v1/permissions')) })
  if (roles.isPending || catalogue.isPending) return <Loading />
  if (roles.isError || catalogue.isError) return <ErrorMessage message={problemMessage(roles.error ?? catalogue.error, t)} />
  const adjustable = catalogue.data.filter((p) => p.adjustable && !p.scopeTypes.every((s) => s === 'PLATFORM'))
  return (
    <>
      {roles.data.filter((role) => role.editable).map((role) => (
        <RoleCard key={role.roleCode} organizationId={organizationId} role={role} catalogue={adjustable} />
      ))}
    </>
  )
}

/** One role: its effective permissions, saved as differences from the defaults, with "back to defaults". */
function RoleCard({ organizationId, role, catalogue }: { organizationId: string; role: OrganizationRole; catalogue: components['schemas']['Permission'][] }) {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Set<PermissionCode>>(new Set(role.effectivePermissions))
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['organizations', organizationId, 'roles'] })
  const path = { organizationId, roleCode: role.roleCode }
  const save = useMutation({
    mutationFn: () => unwrap(api.PUT('/v1/organizations/{organizationId}/roles/{roleCode}', { params: { path }, body: { permissions: [...selected] } })),
    onSuccess: refresh,
  })
  const reset = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/organizations/{organizationId}/roles/{roleCode}/reset', { params: { path } })),
    onSuccess: (data) => {
      setSelected(new Set(data.effectivePermissions))
      void refresh()
    },
  })
  const toggle = (code: PermissionCode) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  return (
    <Card title={t(`roles.${role.roleCode}`, { defaultValue: role.roleCode })}>
      {role.customized && <Badge tone="warn">{t('people.customized')}</Badge>}
      <ul className="grid gap-1 sm:grid-cols-2">
        {catalogue.map((permission) => (
          <li key={permission.code}>
            <label className="flex min-h-10 items-center gap-2 text-sm">
              <input type="checkbox" className="size-5 accent-brand" checked={selected.has(permission.code)}
                onChange={() => toggle(permission.code)} />
              <span>{localize(permission.label)}</span>
              {permission.sensitive && <Badge tone="warn">{t('people.sensitive')}</Badge>}
              {role.defaultPermissions.includes(permission.code) !== selected.has(permission.code) && (
                <Badge>{t('people.changed')}</Badge>
              )}
            </label>
          </li>
        ))}
      </ul>
      <ErrorMessage message={save.error || reset.error ? problemMessage(save.error ?? reset.error, t) : null} />
      <div className="flex flex-wrap gap-2">
        <Button tone="primary" busy={save.isPending} onClick={() => save.mutate()}>{t('common.save')}</Button>
        <Button busy={reset.isPending} onClick={() => reset.mutate()}>{t('people.resetDefaults')}</Button>
      </div>
    </Card>
  )
}
