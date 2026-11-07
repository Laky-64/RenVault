package service

import (
	"context"

	"github.com/Laky-64/renvault-core/bridge"
	"github.com/Laky-64/renvault-core/passgen"
)

func installGenerator() {
	handle(bridge.OpCheckPassword, func(_ context.Context, request checkRequest) (checkResponse, error) {
		rules := passgen.RulesFor(request.Domain)
		return checkResponse{
			Problems:  passgen.Check(request.Password, rules),
			MinLength: rules.MinLength,
			MaxLength: rules.MaxLength,
		}, nil
	})

	handle(bridge.OpGeneratePassword, func(_ context.Context, request generateRequest) (generateResponse, error) {
		rules := passgen.RulesFor(request.Domain)
		if request.Rules != "" {
			rules = passgen.ParseRules(request.Rules)
		}
		options := passgen.Options{
			Kind:    request.Kind,
			Length:  request.Length,
			Digits:  request.Digits,
			Symbols: request.Symbols,
			Words:   request.Words,
		}
		blocked := passgen.Blocked(options, rules)
		digits, symbols := passgen.Needs(rules)
		result, err := passgen.Generate(options, rules)
		low, high := passgen.Bounds(rules)
		return generateResponse{
			Password:  result.Password,
			Kind:      result.Kind,
			Bits:      result.Bits,
			Ruled:     !rules.Empty(),
			MinLength: low,
			MaxLength: high,
			Symbols:   rules.Symbols(),
			Blocked:   blocked,
			Digits:    digits,
			Special:   symbols,
		}, err
	})

	handle(bridge.OpPasswordHistory, func(_ context.Context, request identifierRequest) (historyResponse, error) {
		active, err := unlocked()
		if err != nil {
			return historyResponse{}, err
		}
		versions, err := active.PasswordHistory(request.ID)
		items := make([]versionEntry, 0, len(versions))
		for _, version := range versions {
			items = append(items, versionEntry{Password: version.Password, Until: millis(version.Until)})
		}
		return historyResponse{Items: items}, err
	})

	query(bridge.OpGeneratedList, func(context.Context) (generatedResponse, error) {
		active, err := unlocked()
		if err != nil {
			return generatedResponse{}, err
		}
		list, err := active.GeneratedPasswords()
		items := make([]generatedEntry, 0, len(list))
		for _, g := range list {
			items = append(items, generatedEntry{Password: g.Password, Domain: g.Domain, At: millis(g.At), Saved: g.Saved})
		}
		return generatedResponse{Items: items}, err
	})

	command(bridge.OpGeneratedAdd, func(_ context.Context, request generatedRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.RecordGenerated(request.Password, request.Domain)
	})

	command(bridge.OpGeneratedRemove, func(_ context.Context, request generatedRequest) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.RemoveGenerated(request.Password)
	})

	trigger(bridge.OpGeneratedClear, func(context.Context) error {
		active, err := unlocked()
		if err != nil {
			return err
		}
		return active.ClearGenerated()
	})
}
