_stock_completion() {
    local current previous command candidates
    current="${COMP_WORDS[COMP_CWORD]}"
    previous="${COMP_WORDS[COMP_CWORD-1]}"
    command=""

    local commands="data-collect data-generate model-prepare model-train model-rank model-evaluate trade-buy trade-sell broker-check broker-sync system-pull system-push system-clean -h --help"

    for word in "${COMP_WORDS[@]}"; do
        case "$word" in
            data-collect|data-generate|model-prepare|model-train|model-rank|model-evaluate|trade-buy|trade-sell|broker-check|broker-sync|system-pull|system-push|system-clean)
                command="$word"
                ;;
        esac
    done

    case "$previous" in
        --type)
            candidates="stock daily minute5 minute1"
            ;;
        --scope)
            candidates="market prepared model state"
            ;;
        *)
            case "$command" in
                data-collect) candidates="--date --codes --type -h --help" ;;
                data-generate) candidates="--date -h --help" ;;
                model-prepare) candidates="--date --force -h --help" ;;
                model-train) candidates="--force -h --help" ;;
                model-rank|model-evaluate) candidates="--date -h --help" ;;
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
