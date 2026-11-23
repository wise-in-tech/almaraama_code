import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, problemMessage, unwrap } from '@/shared/api'
import { Badge, Button, Card, ErrorMessage, Loading, Notice, PageHeader } from '@/shared/ui'
import { canOnSite, useMyAccess } from '../api/queries'
import { downloadQrPng, downloadQrSvg, QrCode } from '../components/QrCode'

/**
 * The registration QR code of a residence (D2, criterion 4, ADR-034): shown, downloaded (SVG, PNG), printed with a
 * short instruction in the three languages; replaced (the old one stops working) and activated or deactivated by the
 * promoteur only ({@code registration-qr:manage}).
 */
export function RegistrationQrPage() {
  const { t } = useTranslation()
  const { siteId } = useParams({ strict: false }) as { siteId: string }
  const queryClient = useQueryClient()
  const access = useMyAccess()
  const canManage = canOnSite(access.data, siteId, 'registration-qr:manage')
  const [notice, setNotice] = useState<string | null>(null)
  const key = ['registration-qr', siteId]
  const qr = useQuery({
    queryKey: key,
    queryFn: () => unwrap(api.GET('/v1/sites/{siteId}/registration-qr', { params: { path: { siteId } } })),
  })
  const rotate = useMutation({
    mutationFn: () => unwrap(api.POST('/v1/sites/{siteId}/registration-qr/rotate', { params: { path: { siteId } } })),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data)
      setNotice(t('qr.rotated'))
    },
  })
  const toggle = useMutation({
    mutationFn: (enabled: boolean) =>
      unwrap(
        api.PUT('/v1/sites/{siteId}/registration-qr', {
          params: { path: { siteId } },
          body: { enabled, version: qr.data?.version ?? 0 },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data)
      setNotice(data.enabled ? t('qr.enabledNotice') : t('qr.disabledNotice'))
    },
  })

  if (qr.isPending) return <Loading />
  if (qr.isError) return <ErrorMessage message={problemMessage(qr.error, t)} />
  const data = qr.data
  const error = rotate.error ?? toggle.error

  return (
    <section className="grid gap-4">
      <div className="print:hidden">
        <PageHeader title={t('qr.title')} intro={t('qr.intro')} />
      </div>
      <Card>
        <div className="grid justify-items-center gap-3 text-center">
          <QrCode text={data.registrationUrl} label={t('qr.imageLabel')} className="w-64 max-w-full print:w-[12cm]" />
          <p dir="ltr" className="font-mono text-2xl tracking-widest">
            {data.code}
          </p>
          <Badge tone={data.enabled ? 'good' : 'warn'}>{data.enabled ? t('qr.enabled') : t('qr.disabled')}</Badge>
          <div className="hidden gap-1 print:grid">
            <p lang="ar" dir="rtl">{t('qr.printInstruction', { lng: 'ar' })}</p>
            <p lang="fr">{t('qr.printInstruction', { lng: 'fr' })}</p>
            <p lang="en">{t('qr.printInstruction', { lng: 'en' })}</p>
          </div>
        </div>
      </Card>
      <Notice message={notice} />
      <ErrorMessage message={error ? problemMessage(error, t) : null} />
      <div className="flex flex-wrap gap-2 print:hidden">
        <Button onClick={() => downloadQrSvg(data.registrationUrl, `qr-${data.code}.svg`)}>{t('qr.downloadSvg')}</Button>
        <Button onClick={() => downloadQrPng(data.registrationUrl, `qr-${data.code}.png`)}>{t('qr.downloadPng')}</Button>
        <Button onClick={() => window.print()}>{t('qr.print')}</Button>
        {canManage && (
          <>
            <Button
              tone="danger"
              busy={rotate.isPending}
              onClick={() => {
                if (window.confirm(t('qr.rotateConfirm'))) rotate.mutate()
              }}
            >
              {t('qr.rotate')}
            </Button>
            <Button busy={toggle.isPending} onClick={() => toggle.mutate(!data.enabled)}>
              {data.enabled ? t('qr.disable') : t('qr.enable')}
            </Button>
          </>
        )}
      </div>
    </section>
  )
}
