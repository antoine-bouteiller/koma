#!/usr/bin/env nu
# commit-msg hook: rejects a header that is not a Conventional Commit, which tools/release.nu would
# silently skip.

const HEADER = '^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\([a-z0-9-]+\))?!?: [^A-Z\s](.*[^.\s])?$'
const EXEMPT = '^(Merge |Revert "|fixup! |squash! |amend! )'

def main [file: path] {
  let header = open --raw $file | lines | where {|l| not ($l | str starts-with "#") } | get 0? | default ""
  if ($header =~ $EXEMPT) or ($header =~ $HEADER and ($header | str length) <= 100) { return }
  print --stderr $"Not a Conventional Commit header: ($header)
Expected `type\(scope\)!: subject`, at most 100 characters, where type is one of build, chore, ci,
docs, feat, fix, perf, refactor, revert, style or test, the scope is lowercase, and the subject
starts in lowercase and ends without a period."
  exit 1
}
