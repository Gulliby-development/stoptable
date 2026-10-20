<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# stoptable

stoptable is an offline command-line tool for people who keep a small bus, tram or ferry timetable in a text file. It checks feeds, shows departures, finds earliest-arrival journeys and exports the network as a DOT or Mermaid diagram.

[![CI](https://github.com/Gulliby-development/stoptable/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/stoptable/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Install

You need Java 21 and Maven. From the project directory, build and run the test suite with:

```sh
mvn -B verify
```

Run a command with:

```sh
java -cp target/classes io.github.gulliby.stoptable.Main COMMAND
```

Replace `COMMAND` with a command and its arguments below. A `FILE` argument is a path or `-` to read the feed from standard input.

## Usage

The built-in usage is:

```text
usage: stoptable check FILE
       stoptable board [--day DAY] [--after HH:MM] [--limit N] STOP FILE
       stoptable trip [--day DAY] [--after HH:MM] [--transfer N] FROM TO FILE
       stoptable export [--format dot|mermaid] FILE
```

Defaults are `--day mon`, `--after 00:00`, `--limit 10`, `--transfer 0` and `--format dot`. Run `java -cp target/classes io.github.gulliby.stoptable.Main --help` for the usage lines.

The example feed below is used by the journey example; save this as village.tt. The repository does not ship the feed file.

## Feed format

A feed is UTF-8 text. Blank lines and lines whose first non-space or tab character is `#` are ignored. Declare stops and services before trips that use them. A stop is `stop ID NAME`, a service is `service ID DAYS`, and a trip is `trip ROUTE SERVICE: STOP TIME > STOP TIME ...`. Days are `mon` through `sun`, with comma-separated days or inclusive ranges such as `mon-fri`. Times use 24-hour `HH:MM`.

```text
# village bus
stop HAR Harbor Pier
stop MKT Market Square
stop CHU Church Lane
stop PRK Park Gate
stop SCH School
service WD mon-fri
service WE sat,sun

trip 12 WD: HAR 07:00 > MKT 07:10 > CHU 07:18 > PRK 07:30
trip 12 WD: HAR 08:00 > MKT 08:10 > CHU 08:18 > PRK 08:30
trip 14 WD: MKT 07:12 > SCH 07:35 > PRK 07:50
trip 14 WD: MKT 08:15 > SCH 08:35 > PRK 08:50
trip 20 WE: HAR 09:00 > MKT 09:12 > PRK 09:40
```

## Commands

`check FILE` checks the feed and reports findings, or confirms that it is clean. It also reports a stop whose name another stop declared earlier already has, ignoring ASCII case (finding `TT006`).

`board [--day DAY] [--after HH:MM] [--limit N] STOP FILE` lists departures from a stop on the selected day, at or after the selected time. It shows the destination stop and up to the selected limit.

`trip [--day DAY] [--after HH:MM] [--transfer N] FROM TO FILE` finds the earliest-arrival journey from one stop to another. A transfer value is the number of minutes required between trips; staying on the same trip needs no transfer time.

`export [--format dot|mermaid] FILE` prints the used stops and connections as a network diagram. The default format is DOT.

For example, with the feed saved as `village.tt`, run:

```sh
java -cp target/classes io.github.gulliby.stoptable.Main trip HAR SCH --after 06:30 village.tt
```

The command prints:

```text
HAR to SCH, mon from 06:30, transfer 0 min
leg 1: route 12  HAR 07:00 > MKT 07:10
leg 2: route 14  MKT 07:12 > SCH 07:35
arrive 07:35, 65 min, 1 change
```

## Exit codes

- `0`: the command succeeded.
- `1`: a negative result, such as findings from `check` or no departure or journey.
- `2`: a command, argument, file, UTF-8 or feed-format error.

## Development

Run the full build and tests with:

```sh
mvn -B verify
```

---

Built with BAKED
