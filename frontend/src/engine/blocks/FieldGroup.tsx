import { z } from 'zod'
import { useLocalizedText } from '@/shared/i18n'
import { defineBlock } from '../registry'
import { localizedTextSchema } from '../schema'

const fieldGroupSchema = z.object({
  type: z.literal('FieldGroup'),
  title: localizedTextSchema,
  fields: z
    .array(
      z.object({
        label: localizedTextSchema,
        value: z.union([z.string(), z.number(), localizedTextSchema]),
      }),
    )
    .max(100),
})

type FieldGroupProps = z.output<typeof fieldGroupSchema>

/** Label/value pairs, such as the "Caractéristiques" block generated from attribute definitions (P1). */
function FieldGroup({ title, fields }: FieldGroupProps) {
  const { localize } = useLocalizedText()
  return (
    <section className="grid gap-3 rounded-lg border border-line bg-surface p-4">
      <h3 className="font-semibold">{localize(title)}</h3>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
        {fields.map((field, index) => (
          <div key={index} className="contents">
            <dt className="text-muted">{localize(field.label)}</dt>
            <dd className="tabular-nums">{typeof field.value === 'object' ? localize(field.value) : field.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

/** The "FieldGroup" block: a titled list of labels and values, such as the "Caractéristiques" of an asset. */
export const fieldGroupBlock = defineBlock(fieldGroupSchema, FieldGroup)
