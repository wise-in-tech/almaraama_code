import type { NodeCategory } from '../api/queries'

/** Tone of the badge of a step category (fixed for every circuit, ADR-036). */
export function categoryTone(category: NodeCategory): 'neutral' | 'good' | 'warn' {
  if (category === 'ON_HOLD' || category === 'OPEN') return 'warn'
  if (category === 'RESOLVED' || category === 'CLOSED') return 'good'
  return 'neutral'
}

/** Categories of the tickets still open, for the default filter of a manager. */
export const OPEN_CATEGORIES: NodeCategory[] = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED']

/**
 * What a passage needs before it can be sent (H5, H6): a reason of the list, a comment when always required or for
 * the chosen reason. Returns the missing piece, or null when the form is complete.
 */
export function missingInput(
  transition: { reasonRequired: boolean; reasons?: string[]; commentRequired: boolean; commentRequiredForReasons?: string[] },
  reason: string,
  comment: string,
): 'reason' | 'comment' | null {
  if (transition.reasonRequired && !(transition.reasons ?? []).includes(reason)) return 'reason'
  const needsComment = transition.commentRequired || (transition.commentRequiredForReasons ?? []).includes(reason)
  if (needsComment && comment.trim() === '') return 'comment'
  return null
}
