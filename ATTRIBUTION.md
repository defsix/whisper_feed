# Attribution

076 Feed is a derivative work and is distributed under the **GNU General Public
License v3.0 or later**, the same licence as the project it forks. The full
licence text is in [`LICENSE`](LICENSE).

## Neo Feed

076 Feed is a fork of **Neo Feed**.

- Source: https://github.com/NeoApplications/Neo-Feed
- Forked at commit: `42e023c2f454229055bca35bebdbdbd18ad5f0e3`
- Copyright © 2025 Saul Henriquez and Antonios Hazim ([Neo Applications](https://github.com/NeoApplications))
- Licence: GPLv3+

Neo Feed contributed the entire initial codebase, and most importantly the
launcher minus-one overlay integration — the `OverlayService` and the
`:google-gsa` module that together implement the
`com.android.launcher3.WINDOW_OVERLAY` provider protocol. 076 Feed retains
that work rather than reimplementing it.

## Upstream of Neo Feed

Neo Feed is itself derived from earlier work, and those credits carry forward:

- **HomeFeeder** by [iTaysonLab](https://github.com/iTaysonLab) — the project Neo Feed was originally forked from.
- **[DrawerOverlayService](https://github.com/FabianTerhorst/DrawerOverlayService)** by Fabian Terhorst — the basis for the overlay service implementation.
- **[Phosphor Icons](https://phosphoricons.com/)** by Helena Zheng and Tobias Fried — icon set used in the app.

Neo Feed's translations are contributed by its community via
[Weblate](https://hosted.weblate.org/engage/neo-feed/); translated strings
inherited by this fork originate there.

## Interoperability note

The `:google-gsa` module implements Google's launcher overlay protocol so that
launchers such as Lawnchair can bind to this app as a feed provider. It is an
independent reimplementation for interoperability, inherited from upstream. It
is not affiliated with, endorsed by, or derived from Google source code, and
076 Feed is not affiliated with Google, Lawnchair, or Feedly.

## Source availability

As required by the GPL, the complete corresponding source for 076 Feed is
available at the repository this file ships in. Any redistributed binary must
be accompanied by, or offer access to, that source under the same licence.
