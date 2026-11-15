# BalanceHub 1.1.4-rc17 — build 701 QA snapshot

**Source:** `4749759a73a95461bc729ae04bd9ab9d233dd98a`  
**Release tag:** `v1.1.4-rc17-build701` (GitHub draft prerelease)  
**Status:** owner-tested pre-release, not a production release.

## Confirmed on the owner's Android handset

| Test | Result |
| --- | --- |
| Payment notification mechanism | Previously tested successfully and retained |
| Event notifications: melody “Капля” | Passed |
| Event notifications: long vibration | Passed |
| Event and update notifications, further user testing | Reported successful |
| Short vibration | Passed |
| Double short vibration | Passed |
| Default vibration | Passed |
| Vibration disabled: notification without vibration | Passed |
| Sound and vibration controls for notifications | Passed in the reported tests |

## Not yet tested

- Delivery and physical sound/vibration **with the screen locked**.
- Reliability of scheduled notifications under prolonged background restrictions and Do Not Disturb.

These checks are deferred; they must not be reported as passed.

## CI reference

Successful build workflow #401 and emulator-validation workflow #103 completed on `feature/balancehub-foundation` at the same source SHA. GitHub CI checks complement, but do not replace, the handset tests.

## Repository maintenance

Maintenance changes should not modify application source, resource sounds, `applicationId`, signing secrets or scheduler behavior. The exact source snapshot remains at `backup/balancehub-701-verified-notifications`.
