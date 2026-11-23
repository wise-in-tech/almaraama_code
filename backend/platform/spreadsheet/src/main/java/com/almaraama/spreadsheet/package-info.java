/**
 * Technical module (ADR-027 revision 2): safe reading of {@code .xlsx} files and production of import templates with
 * Apache POI. Not a bounded context: no domain, no table, no event, no Spring. Used only by the adapters of the
 * modules that import Excel files ({@code sites}, {@code membership}), checked by ArchUnit.
 */
package com.almaraama.spreadsheet;
