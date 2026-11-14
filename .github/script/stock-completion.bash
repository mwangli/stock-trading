# AI_GENERATE_START --
_stock_completion() {
    local current previous command candidates
    current="${COMP_WORDS[COMP_CWORD]}"
    previous="${COMP_WORDS[COMP_CWORD-1]}"
    command=""

    local commands="data-collect data-generate model-prepare model-train model-backtests model-rank model-evaluate trade-buy trade-sell broker-check broker-sync system-pull system-push system-clean -h --help"

    for word in "${COMP_WORDS[@]}"; do
        case "$word" in
            data-collect|data-generate|model-prepare|model-train|model-backtests|model-rank|model-evaluate|trade-buy|trade-sell|broker-check|broker-sync|system-pull|system-push|system-clean)
                command="$word"
                ;;
        esac
    done

    case "$previous" in
        --level)
            candidates="1 2 3"
            ;;
        --scope)
            candidates="market prepared model backtest state"
            ;;
        *)
            case "$command" in
                data-collect) candidates="--date --codes --level -h --help" ;;
                data-generate) candidates="--date --level -h --help" ;;
                model-prepare) candidates="--date --force --model -h --help" ;;
                model-train) candidates="--force --model -h --help" ;;
                model-backtests) candidates="--force --model --backtest-id --exit-position --exit-fallback-position --rank-grace-days --max-holding-days --capital --config -h --help" ;;
                model-rank) candidates="--date --model -h --help" ;;
                model-evaluate) candidates="--date -h --help" ;;
                trade-buy|trade-sell) candidates="-h --help" ;;
                broker-check) candidates="--force --resume -h --help" ;;
                broker-sync) candidates="--date -h --help" ;;
                system-pull) candidates="--scope -h --help" ;;
                system-push) candidates="--scope --force -h --help" ;;
                system-clean) candidates="--date -h --help" ;;
                *) candidates="$commands" ;;
            esac
            ;;
    esac

    COMPREPLY=( $(compgen -W "$candidates" -- "$current") )
}
complete -F _stock_completion stock
# AI_GENERATE_END --
