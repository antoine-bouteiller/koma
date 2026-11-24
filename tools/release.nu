#!/usr/bin/env nu
# Publishes the GitHub release v<version>, carrying koma.apk, when the commits since the last tag
# call for one. `main` releases X.Y.Z and `develop` releases X.Y.Z-beta.N.

const SECTIONS = [
  "⚠ BREAKING CHANGES"
  "🎉 New Features"
  "🐛 Bug Fixes"
  "📦 Dependency Updates"
  "📚 Documentation"
]

def main [--dry-run] {
  let branch = $env.GITHUB_REF_NAME? | default (git branch --show-current | str trim)
  let beta = match $branch {
    "main" => false
    "develop" => true
    _ => { error make {msg: $"releases come from main or develop, not ($branch)"} }
  }
  let tags = git tag --merged HEAD --list 'v*' | lines | each {|tag| parse-version $tag }
  let stable = $tags | where beta == null
  let last = if $beta { $tags } else { $stable } | sort-by {|v| rank $v } | last
  let all = git log $"($last.tag)..HEAD" -z --format="%H%n%B"
    | split row (char nul)
    | where $it != ""
    | each {|raw| parse-commit $raw }
  let commits = $all | drop-reverted
  let types = $commits | each {|c| release-type $c }
  let type = if "minor" in $types { "minor" } else if "patch" in $types { "patch" } else { null }
  print $"Last release: ($last.tag), ($all | length) commits since."
  if $type == null {
    print "There are no relevant changes, so no new version is released."
    return
  }

  let next = if not $beta {
    bump $last $type
  } else if $last.beta == null {
    bump $last $type | update beta 1
  } else {
    let latest_stable = $stable | sort-by {|v| rank $v } | last
    [($last | update beta ($last.beta + 1)) (bump $latest_stable $type | update beta 1)]
    | sort-by {|v| rank $v }
    | last
  }
  let version = format-version $next
  let repo = repo-url
  let notes = release-notes $commits $version $last.tag $repo
  print $"Next release: v($version)."
  if $dry_run {
    print $notes
    return
  }

  with-env {KOMA_VERSION: $version} { ./gradlew --no-daemon assembleRelease }
  cp app/build/outputs/apk/release/app-release.apk koma.apk
  # Checked after the build, which takes minutes: a newer run releases these commits instead.
  git fetch --quiet origin $branch
  if (git merge-base --is-ancestor FETCH_HEAD HEAD | complete).exit_code != 0 {
    print $"origin/($branch) has moved past (git rev-parse --short HEAD | str trim), so no new version is released."
    return
  }
  let prerelease = if $beta { [--prerelease] } else { [] }
  (
    $notes | gh release create $"v($version)" koma.apk
      --target (git rev-parse HEAD | str trim)
      --title $"v($version)"
      --notes-file -
      ...$prerelease
  )
}

def parse-version [tag: string] {
  let match = $tag
    | parse --regex '^v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?:-beta\.(?<beta>\d+))?$'
  if ($match | is-empty) { return null }
  let m = $match | first
  {
    tag: $tag
    major: ($m.major | into int)
    minor: ($m.minor | into int)
    patch: ($m.patch | into int)
    beta: (if $m.beta == null { null } else { $m.beta | into int })
  }
}

# A stable release outranks its betas.
def rank [v: record] {
  (($v.major * 1000 + $v.minor) * 1000 + $v.patch) * 10000 + ($v.beta | default 9999)
}

# Koma stays at 0.x, so a breaking change bumps the minor version like a feature.
def bump [v: record, type: string] {
  match $type {
    "minor" => {major: $v.major, minor: ($v.minor + 1), patch: 0, beta: null}
    "patch" => {major: $v.major, minor: $v.minor, patch: ($v.patch + 1), beta: null}
  }
}

def format-version [v: record] {
  $"($v.major).($v.minor).($v.patch)" + (if $v.beta == null { "" } else { $"-beta.($v.beta)" })
}

def parse-commit [raw: string] {
  let lines = $raw | lines
  let header = $lines | get 1? | default ""
  let body = $lines | skip 2 | str join "\n"
  let match = $header
    | parse --regex '^(?<type>\w+)(?:\((?<scope>[^)]*)\))?(?<bang>!)?: (?<subject>.+)$'
  let footer = $body | parse --regex '(?s)BREAKING[ -]CHANGE: (?<note>.+)$'
  let reverts = $body | parse --regex 'This reverts commit (?<hash>[0-9a-f]{7,40})' | get 0?.hash
  let base = {hash: $lines.0, type: null, scope: null, subject: $header, breaking: null, reverts: $reverts}
  if ($match | is-empty) {
    return ($base | update type (if ($header | str starts-with 'Revert "') { "revert" }))
  }
  let m = $match | first
  let breaking = if ($footer | is-not-empty) {
    $footer.0.note | str trim
  } else if $m.bang != null {
    $m.subject
  }
  $base | merge {type: $m.type, scope: $m.scope, subject: $m.subject, breaking: $breaking}
}

# A commit and its revert in the same release cancel out. `commits` is newest first.
def drop-reverted [] {
  let commits = $in
  $commits
  | reverse
  | reduce --fold [] {|c, kept|
    let target = if $c.reverts == null { null } else {
      $kept | enumerate | where {|k| $k.item.hash | str starts-with $c.reverts } | get 0?.index
    }
    if $target == null { $kept | append $c } else { $kept | drop nth $target }
  }
  | reverse
}

def release-type [c: record] {
  if $c.breaking != null or $c.type == "feat" {
    "minor"
  } else if $c.type in [fix perf revert] or ($c.type == "build" and $c.scope == "deps") {
    "patch"
  }
}

def section [c: record] {
  match [$c.type $c.scope] {
    ["feat" _] => "🎉 New Features"
    ["fix" _] => "🐛 Bug Fixes"
    ["build" "deps"] => "📦 Dependency Updates"
    ["docs" _] => "📚 Documentation"
    _ => null
  }
}

def repo-url [] {
  if $env.GITHUB_REPOSITORY? != null {
    $"($env.GITHUB_SERVER_URL)/($env.GITHUB_REPOSITORY)"
  } else {
    gh repo view --json url --jq .url | str trim
  }
}

def release-notes [commits: list, version: string, last: string, repo: string] {
  let date = date now | date to-timezone UTC | format date "%Y-%m-%d"
  let breaking = $commits
    | where breaking != null
    | each {|c| {section: $SECTIONS.0, scope: $c.scope, text: $c.breaking, hashes: []} }
  let listed = $commits
    | each {|c| section $c | if $in != null { {section: $in, scope: $c.scope, text: $c.subject, hashes: [$c.hash]} } }
  let entries = [
    ...$breaking
    ...($listed | where section != $SECTIONS.3)
    ...(merge-bumps ($listed | where section == $SECTIONS.3))
  ]
    | each {|e|
      let scope = if $e.scope == null { "" } else { $"**($e.scope):** " }
      let links = $e.hashes
        | each {|h| $"[($h | str substring 0..6)]\(($repo)/commit/($h)\)" }
        | str join ", "
      let line = if $links == "" { $"* ($scope)($e.text)" } else { $"* ($scope)($e.text) \(($links)\)" }
      {section: $e.section, scope: $e.scope, line: $line}
    }
    | sort-by {|e| $"($e.scope | default '')\n($e.line)" }
  let body = $SECTIONS
    | each {|title|
      let lines = $entries | where section == $title | get line
      if ($lines | is-not-empty) { [$"### ($title)" "" ...$lines ""] | str join "\n" }
    }
    | str join "\n"
  $"## [($version)]\(($repo)/compare/($last)...v($version)\) \(($date)\)\n\n($body)"
}

# Renovate titles a bump `update <package> ( <from> ➜ <to> )`. Several bumps of one package in a
# release become one entry, from the oldest bump's version to the newest's, linking every commit.
def merge-bumps [entries: list] {
  $entries
  | reverse
  | each {|e|
    let bump = $e.text | parse --regex '^(?<package>.+) \( (?<from>\S+) ➜ (?<to>\S+) \)$'
    $e | merge ($bump | get 0? | default {package: $e.text, from: null, to: null})
  }
  | group-by --to-table scope package
  | each {|g|
    let first = $g.items | first
    let last = $g.items | last
    let text = if $first.from == null or ($g.items | length) == 1 {
      $first.text
    } else {
      $"($first.package) \( ($first.from) ➜ ($last.to) \)"
    }
    {section: $first.section, scope: $first.scope, text: $text, hashes: ($g.items | get hashes | flatten)}
  }
}
