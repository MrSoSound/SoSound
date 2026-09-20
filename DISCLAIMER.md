# Disclaimer

## What it is, and what it isn't

SoSound is a music player that searches tracks through YouTube Music's
internal API (InnerTube) and downloads them locally for offline
listening, using [yt-dlp](https://github.com/yt-dlp/yt-dlp) bundled via
[youtubedl-android](https://github.com/junkfood02/youtubedl-android).

**It is not a service.** There is no server run by the author in the
middle: every search and every download is a direct connection between
the device running the app and YouTube's servers, the same kind of
request a browser would make. The author does not host, transmit, store,
or have access to any audio file downloaded by anyone using the app.

**It is not affiliated with YouTube.** SoSound is not a product of
Google, YouTube, Alphabet Inc., or Spotify AB, and is not sponsored,
endorsed, or in any way associated with them. "YouTube", "YouTube Music",
and "Spotify" are trademarks of their respective owners, mentioned here
only to describe what the app interacts with.

The InnerTube API used for search is not a documented public interface:
it's the one the official site and app use internally, and it has been
observed and replicated (the same is true, more generally, of how
yt-dlp resolves audio URLs). It can change at any time without notice,
and when it does the app may stop working until it's updated — there is
no guarantee of continued operation.

## No warranty

The software is distributed under the terms of the **GNU GPL v3.0**
license (see [`LICENSE`](LICENSE)), which explicitly states, in
sections 15 and 16, that there is no warranty of any kind: NO WARRANTY
WHATSOEVER, not even the implied warranty of merchantability or fitness
for a particular purpose. To the maximum extent permitted by applicable
law, the author is not liable for damages arising from the use or
inability to use the software.

## Responsibility of whoever uses the app

SoSound is a general-purpose tool: it lets you search, download, and
listen offline to tracks accessible through the YouTube Music API,
which includes both freely distributable content and copyrighted
content. As with any tool of this kind (a browser, a disc burner, a
camera), it is **the user** — not the author — who must verify they
have the right to download and keep that content under the copyright
laws of their own jurisdiction and YouTube's Terms of Service. The
author does not control, moderate, or have any way to control how
third parties use it.

## The relevant precedent

In 2020, GitHub temporarily took down the **youtube-dl** repository
following a DMCA notice from the RIAA — not for direct copyright
infringement, but for an alleged circumvention of a technical
protection measure (DMCA §1201, YouTube's "rolling cipher"). The
[Electronic Frontier Foundation](https://www.eff.org/deeplinks/2020/11/riaa-abuses-dmca-take-down-youtube-dl)
stepped in and the repository was restored, on the grounds that a tool
capable of substantial non-infringing uses — youtube-dl can also
download freely distributable, public-domain, or author-licensed
content — does not by itself circumvent an effective protection measure
and is not illegal for that reason. SoSound sits in the same territory:
a general-purpose tool, not a service that distributes protected
content.

## No commercial support

This is a personal project, published as-is, with no commitment to
maintenance, support, or future compatibility.
