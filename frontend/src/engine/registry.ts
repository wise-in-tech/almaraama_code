import { createElement, type ComponentType, type ReactElement } from 'react'
import type { z } from 'zod'

/** Outcome of rendering a block: the element, or why it was refused. */
export type BlockRenderResult = { ok: true; element: ReactElement } | { ok: false; reason: string }

/** A component the engine is allowed to render, with the schema its definition must satisfy. */
export interface RegisteredBlock {
  render(definition: unknown): BlockRenderResult
}

/** Registers a block: its definition is validated by `schema` before `Component` ever sees it. */
export function defineBlock<S extends z.ZodType<Record<string, unknown>>>(
  schema: S,
  Component: ComponentType<z.output<S>>,
): RegisteredBlock {
  return {
    render(definition) {
      const result = schema.safeParse(definition)
      if (!result.success) {
        return { ok: false, reason: result.error.message }
      }
      // The schema guarantees the props; the cast only widens the generic for createElement.
      const props = result.data as Record<string, unknown>
      return { ok: true, element: createElement(Component as ComponentType<Record<string, unknown>>, props) }
    },
  }
}

/** The closed list of components a screen may use. Adding one is a code review, not a configuration. */
export type Registry = Readonly<Record<string, RegisteredBlock>>

/** Builds the closed registry of blocks from groups of definitions; only these types can ever be rendered. */
export function createRegistry(...groups: Array<Record<string, RegisteredBlock>>): Registry {
  return Object.freeze(Object.assign({}, ...groups) as Record<string, RegisteredBlock>)
}

/** The registered block for a type, or undefined when the type is not allowed. */
export function findBlock(registry: Registry, type: string): RegisteredBlock | undefined {
  return Object.hasOwn(registry, type) ? registry[type] : undefined
}
