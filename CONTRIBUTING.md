# Contributing to GenBrush

Thank you for your interest in contributing! This guide will help you get started.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/Rooftop-Love/genbrush.git`
3. Create a branch: `git checkout -b feature/your-feature-name`
4. Make your changes
5. Push and create a Pull Request

## Development Environment

- **Android Studio**: Ladybug (2024.2) or newer
- **JDK**: 17 or newer
- **Min SDK**: 24, **Target SDK**: 36

## Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use Kotlin DSL for Gradle scripts
- Compose functions should be annotated with `@Preview` where applicable
- Run `./gradlew test lintDebug` before submitting (the same checks CI runs; ktlint/detekt are not configured)

## Commit Messages

Use clear, descriptive commit messages:

```
feat: add image batch generation support
fix: resolve crash on API timeout
docs: update README with build instructions
refactor: extract common UI components
```

## Pull Request Guidelines

- Keep PRs focused on a single change
- Include a clear description of what changed and why
- Add screenshots for UI changes
- Ensure the app builds and tests pass
- Link related issues

## Reporting Bugs

Use the [Bug Report](https://github.com/Rooftop-Love/genbrush/issues/new?template=bug_report.md) template. Include:

- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots or screen recordings if applicable

## Feature Requests

Use the [Feature Request](https://github.com/Rooftop-Love/genbrush/issues/new?template=feature_request.md) template.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
