# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| main    | Yes                |
| develop | Yes (pre-release)  |

## Reporting a Vulnerability

If you discover a security vulnerability in Twende, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, use one of these methods:

1. **GitHub Private Vulnerability Reporting** — use the "Report a vulnerability" button on the [Security tab](../../security/advisories/new)
2. **Email** — send details to security@twende.app

### What to include

- Description of the vulnerability
- Steps to reproduce
- Affected service(s) and version(s)
- Potential impact
- Suggested fix (if any)

### Response timeline

- **Acknowledgement**: within 48 hours
- **Assessment**: within 5 business days
- **Fix**: critical vulnerabilities patched within 7 days

## Security Measures

This project employs:

- **Trivy** — dependency vulnerability and secret scanning (CI + local pre-push)
- **CodeQL** — static analysis for SQL injection, XSS, and other OWASP vulnerabilities
- **Dependabot** — automated dependency update alerts
- **Secret scanning** — detects leaked credentials in commits
- **JaCoCo** — 80% minimum test coverage enforcement
- **Spring Security** — OAuth2 JWT authentication, rate limiting, CORS

## Responsible Disclosure

We follow responsible disclosure practices. If you report a vulnerability:

- We will not take legal action against you
- We will credit you in the fix (unless you prefer anonymity)
- We will coordinate disclosure timing with you
