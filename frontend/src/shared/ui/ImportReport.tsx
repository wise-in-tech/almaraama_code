import { useTranslation } from 'react-i18next'
import { Notice, Table } from './index'

/** The report of an Excel import, as the contract defines it ({@code UnitImportReport}, {@code ResidentImportReport}). */
export type ImportReport = {
  outcome: string
  totalRows: number
  createdCount: number
  errors: { row: number; column?: string; code: string; value?: string }[]
}

/** What an import did: outcome and every error by Excel row and column (ADR-027). */
export function ImportReportView({ report }: { report: ImportReport }) {
  const { t } = useTranslation()
  return (
    <div className="grid gap-2">
      <Notice message={t(`import.outcome.${report.outcome}`, { total: report.totalRows, created: report.createdCount })} />
      {report.errors.length > 0 && (
        <Table
          headers={[t('import.row'), t('import.column'), t('import.error'), t('import.value')]}
          rows={report.errors.map((e) => [String(e.row), e.column ? t(`import.columns.${e.column}`) : '',
            t([`errors.codes.${e.code}`, 'errors.generic']), e.value ?? ''])}
        />
      )}
    </div>
  )
}
