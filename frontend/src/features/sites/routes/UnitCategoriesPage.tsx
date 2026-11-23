import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { useLocalizedText } from '@/shared/i18n'
import { Badge, Card, ErrorMessage, Loading, PageHeader, Table } from '@/shared/ui'
import { CategoryForm } from '../components/CategoryForm'

/**
 * The unit categories usable in an organization (A6, criterion 2): the platform defaults (typologies studio, T1 to
 * T5, duplex…) and the organization's own, which the promoteur adds.
 */
export function UnitCategoriesPage() {
  const { t } = useTranslation()
  const { localize } = useLocalizedText()
  const { organizationId } = useParams({ strict: false }) as { organizationId: string }
  const queryClient = useQueryClient()
  const categories = useQuery({
    queryKey: ['unit-categories', organizationId],
    queryFn: () => unwrap(api.GET('/v1/unit-categories', { params: { query: { organizationId } } })),
  })
  const create = useMutation({
    mutationFn: (draft: Parameters<Parameters<typeof CategoryForm>[0]['onSubmit']>[0]) =>
      unwrap(api.POST('/v1/unit-categories', { body: { scope: { type: 'ORGANIZATION', id: organizationId }, ...draft } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['unit-categories', organizationId] }),
  })
  return (
    <section className="grid gap-4">
      <PageHeader title={t('categories.title')} intro={t('categories.intro')} />
      <Card title={t('categories.addTitle')}>
        <CategoryForm busy={create.isPending} onSubmit={(draft) => create.mutate(draft)} />
        <ErrorMessage message={create.error ? problemMessage(create.error, t) : null} />
      </Card>
      {categories.isPending && <Loading />}
      {categories.data && (
        <Table
          headers={[t('units.type'), t('categories.code'), t('categories.name'), '']}
          rows={categories.data.map((c) => [t(`unitType.${c.unitType}`), c.code, localize(c.name),
            <Badge>{t(`scope.${c.scope.type}`)}</Badge>])}
        />
      )}
    </section>
  )
}
