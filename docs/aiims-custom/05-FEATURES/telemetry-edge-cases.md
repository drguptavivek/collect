# Telemetry Edge Cases

> Last Updated: 2025-12-30

## Acceptable Data Loss: Offline >2 Days

### Scenario
Device is offline for extended period (>2 days past token expiry). Pending telemetry events will be lost.

### Why Acceptable
1. **VG server has 2-day grace** for telemetry submission - generous window
2. **Rare situation** - device offline >2 days indicates bigger issues
3. **Security tradeoff** - storing tokens per event adds complexity and risk
4. **Flush-before-clear** handles 99% of real-world cases

### Behavior Matrix

| Scenario | Telemetry | Data Loss |
|----------|-----------|-----------|
| Online logout | Flushed | None |
| Token expired <2d | Server accepts | None |
| Offline logout | Pending | None (can't logout yet) |
| Offline >2d then online | 401 rejected | **Acceptable loss** |

### Mitigations
- Worker retries every 20min while token valid
- Flush queue before clearing token on logout
- Log flush failures for audit trail

### Beads Reference
- `collect-2px.4`: Documentation of this edge case
- `collect-a22`: Offline storage implementation
