# compiler/

FlowTail content compiler, Python 3.10+, standard library only. Design: `docs/13_PHASE2_DESIGN.md`.

```sh
python3 compiler/flowtailc.py check examples/example-plugin      # validate only (file:line errors)
python3 compiler/flowtailc.py build examples/example-plugin -o build/plugins
python3 compiler/flowtailc.py pack build/plugins/example.ftplugin --state content-state -o build/pack
bash scripts/build-content.sh                                    # all of the above for the example plugin
python3 -m unittest discover -s compiler/tests                   # 13 tests
```
Rules: every error prints `file:line: error[CODE]: what` and a `fix:` line. Output is deterministic. `content-state/allocations.json` is server state: keep it with the world, never edit it by hand.
