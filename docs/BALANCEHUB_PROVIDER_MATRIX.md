# BalanceHub provider capability matrix

This is an implementation policy, not a claim that an integration is already available.

| Source | Preferred integration | Fallback | Initial status |
| --- | --- | --- | --- |
| MTS | Official documented balance/account API, if access is granted | Manual balance and last-updated timestamp | Not integrated |
| Tele2 | Official documented balance/account API, if access is granted | Manual balance and last-updated timestamp | Not integrated |
| Bank A / Bank B / other banks | Official read-only Open Banking / PSD2 connection where supported | Manual balance or statement import | Not integrated |

## Adapter contract
Each adapter should expose:
- provider identifier and display name;
- supported capabilities (balance read, account list, refresh);
- authorization state;
- refresh result with amount, currency and observation time;
- typed errors: unsupported, authentication required, permission denied, rate limited, network error, provider error.

## Refresh rules
- Refresh on user request and on a conservative periodic schedule.
- Respect provider rate limits and Android background execution limits.
- Retain the last known value with a visible timestamp when refresh fails.
- Never infer a successful refresh from a cached response.
- Do not ask users to provide banking passwords or one-time codes to the app.

## Currency rules
A consolidated total is shown only for a single currency. Multi-currency balances remain grouped by currency until a transparent exchange-rate source is selected by the user.
