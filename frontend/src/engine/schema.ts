import { z } from 'zod'

/** Text in every supported language (`LocalizedText` in api/openapi.yaml). */
export const localizedTextSchema = z.object({ ar: z.string(), en: z.string(), fr: z.string() })

/**
 * A screen definition (ADR-021, ADR-024): data only, never code. Each block names a type that must exist in the
 * registry; its other properties are validated by that block's own schema.
 */
export const screenDefinitionSchema = z.object({
  screen: z.string().min(1),
  version: z.number().int().positive(),
  title: localizedTextSchema.optional(),
  blocks: z.array(z.looseObject({ type: z.string().min(1) })).max(200),
})

/** A validated screen definition. */
export type ScreenDefinition = z.infer<typeof screenDefinitionSchema>
/** One block of a screen definition, before its own schema is checked. */
export type BlockDefinition = ScreenDefinition['blocks'][number]

/** Validates untrusted input (from the API, from an AI proposal) before anything is rendered. */
export function parseScreenDefinition(input: unknown) {
  return screenDefinitionSchema.safeParse(input)
}
