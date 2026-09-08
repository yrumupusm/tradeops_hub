# Fictional Trade Transaction CSV Dictionary

This import exists only to demonstrate an internal CSV validation workflow. No committed row may identify a real company, person, order, or customer.

| Column | Required | Rule |
|---|---:|---|
| `transactionId` | yes | Unique, 3–100 alphanumeric/hyphen characters. Duplicate values are not imported. |
| `transactionDate` | yes | ISO date (`YYYY-MM-DD`). |
| `counterpartyName` | yes | Fictional name, 1–255 characters. |
| `countryCode` | no | Two uppercase letters. |
| `amountUsd` | yes | Decimal greater than zero; maximum two fraction digits. |
| `currency` | yes | Three uppercase letters. |
| `dataOrigin` | yes | Must be `FICTIONAL`. |

A rejected row stores only its source row number, stable error code, and field name. Raw uploaded data is never logged or returned in errors.