# Using LingoLLM on a phone

LingoLLM has a layout made for phones. The phone layout is **not a copy of the desktop one**: it is simpler, a few things
are missing and not every extension works in it. This page lists the differences.

## When the phone layout is used

LingoLLM looks at the width of the browser window **when the page loads**. Narrower than 768 pixels gives you the phone
layout, anything wider the normal [workspace](index.md#the-workspace).

- Tablets and phones in landscape mode are usually wider than 768 pixels and get the desktop layout.
- The layout is **not switched while you work**. If you turn the phone or tablet on its side, or resize the window, the
  layout stays as it was until you **reload the page**.
- A narrow desktop browser window gets the phone layout too, which is handy to try it.

Like the desktop, the phone layout can be added to the home screen like an app (*Install app* / *Add to Home Screen*
in the browser menu).

## The workspace

The tabs on the left are replaced by a **drawer**. Tap the menu button in the top bar to open it, tap a tab to switch
to it. The tab you are on fills the whole screen and its name is shown in the top bar.

The drawer has the same tabs as the desktop - *Learning*, *Settings*, *Inference Providers*, *Resources* and *Trash* -
and below them the interface **Language**, **Change Password** and **Logout**. The *Learning* tab, with the
[language selector](language-profiles.md), is the same as on the desktop.

Differences from the desktop:

- The *Settings* header stacks its buttons under the title. The settings and their extension panels are the same.
- *Inference Providers* and *Trash* list each entry as text with the buttons next to it, instead of a wide table.

!!!warning Administrators
An administrator on a phone is a normal user: there is **no Admin button**. The administrator tasks have to be done in
the desktop layout, that is in a window wider than 768 pixels (reload the page after widening it).
!!!

## Extensions

The phone layout has its own classes, so an extension has to support it explicitly. Extensions that don't may add
nothing at all on the phone; they appear where their author made them work. If an extension seems to be missing,
check it in the desktop layout. How an extension supports the phone is described in the
[developer manual](../developer/plugins/extendable.md#the-phone-twins).

## Tips

- If you need something that is missing on the phone, turn the phone on its side and reload, or open LingoLLM in a
  wider window; your profiles and settings are the same everywhere.
- To reach LingoLLM from a phone it has to run as a [server](getting-started/docker-server.md): the
  [desktop app](getting-started/desktop-app.md) listens on your computer only.
