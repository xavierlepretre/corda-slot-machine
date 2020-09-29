// This is a replacement for the DOM customizations which used to be implemented by PHP
// I implement this by reading from the top of index.php
// This source code uses property names, if it's minified these names ought to be changed to string literals.

(function () {
  // WindowID is used to identify each Window, in case the user opens more than one at a time, and spins in all of them.
  // Sent straight up to the server.
  // PHP server ignores this except for logging, so here I don't even bother to randomise it.
  window.windowID = 4; // https://xkcd.com/221/

  // this emulates GameUtils::DemandLoginOnRender() and Users::GetUserBalance($userID)
  function getUserInfo() {
    return { userID: 42, userBalance: 100 }; // FIXME
  }
  const { userID, userBalance } = getUserInfo();
  // here we're supposed to emulate GameUtils::DemandLoginOnRender() and Users::GetUserBalance($userID)
  // we'll do this when the next script -- i.e. in login.js before slot.js
  window.remaining_balance = 0;

  const gameType = "default"; // Modify on this line which game Type you'd like to show
  const MIN_BET = 1;
  const MAX_BET = 999999999;
  const ICONS_PER_REEL = 6;
  const DEFAULT_TIMEOUT = 30000; // 30 seconds

  // this emulates Slots::GetGameSettings($gameType, true)
  function getGameSettings(gameType) {
    return {
      game_type: gameType,
      min_bet: MIN_BET,
      max_bet: MAX_BET,
      icons_per_reel: ICONS_PER_REEL,
    };
  }
  const gameSettings = getGameSettings(gameType);
  const $elSlotsOuterContainer = $(".slot_machine_outer_container");
  $elSlotsOuterContainer.attr(
    "data-game-settings",
    JSON.stringify(gameSettings)
  );

  // this emulates PrizesAndReels::PrizesForGameType($gameType)
  function prizesForGameType(gameType) {
    const rawDbData = [
      [1, "default", 6, 6, 6, 0.0003, 200, 200],
      [2, "default", 4, 4, 4, 0.0015, 50, 50],
      [3, "default", 2, 2, 2, 0.0035, 20, 20],
      [4, "default", "1/3", "5/2", "4/6", 0.0045, 15, 15],
      [5, "default", 5, 5, 5, 0.0055, 13, 13],
      [6, "default", 1, 1, 1, 0.008, 12, 12],
      [7, "default", 3, 3, 3, 0.01, 10, 10],
      [8, "default", "1/3/5", "1/3/5", "1/3/5", 0.09, 4, 4],
    ];
    const dbPrizes = rawDbData.map((row) => {
      return {
        id: row[0],
        game_type: row[1],
        reel1: row[2],
        reel2: row[3],
        reel3: row[4],
        probability: row[5],
        payout_credits: row[6],
        payout_winnings: row[7],
      };
    });
    // May return an array or a string, depending on whether the matching rule has many options
    // Example input: '1/2/3` or `*.5`
    // Returns: ['1', '2', '3']
    function parseReelRule(rule) {
      return isNaN(rule) ? rule.split("/") : rule.toString();
    }
    return dbPrizes.map((prizeData) => {
      prizeData.reel1_parsed = parseReelRule(prizeData.reel1);
      prizeData.reel2_parsed = parseReelRule(prizeData.reel2);
      prizeData.reel3_parsed = parseReelRule(prizeData.reel3);
      return prizeData;
    });
  }
  // this emulates PrizesAndReels::ImageClassNameFromMatchingRule($rule)
  function imageClassNameFromMatchingRule(rule) {
    return (
      "prize_" +
      (isNaN(rule)
        ? rule
            .replace(/ /g, "")
            .replace(/\*/g, "star")
            .replace(/\./g, "dot")
            .replace(/\//g, "slash")
        : rule.toString())
    );
  }
  // this emulates PrizesAndReels::ListPrizesForRendering($gameType)
  function listPrizesForRendering(gameType) {
    return prizesForGameType(gameType).map((row) => {
      row.reel1_classname = imageClassNameFromMatchingRule(row.reel1);
      row.reel2_classname = imageClassNameFromMatchingRule(row.reel2);
      row.reel3_classname = imageClassNameFromMatchingRule(row.reel3);
      return row;
    });
  }
  const prizes = listPrizesForRendering(gameType);
  // this displays winning reels by adding to a template defined in the HTML
  const $elPrizesList = $elSlotsOuterContainer.find(
    ".slot_machine_prizes_list"
  );
  const htmlTemplatePrizes = $("#template_prizes").html();
  prizes.forEach((prize) => {
    const $el = $(htmlTemplatePrizes);
    $el
      // .find(".slot_machine_prize_row")
      .addClass("slot_machine_prize_row_" + prize.id);
    $el.find(".slot_machine_prize_reel1").addClass(prize.reel1_classname);
    $el.find(".slot_machine_prize_reel2").addClass(prize.reel2_classname);
    $el.find(".slot_machine_prize_reel3").addClass(prize.reel3_classname);
    $el
      .find(".slot_machine_prize_payout")
      .attr("data-basePayout", prize.payout_winnings)
      .text(prize.payout_winnings);
    $elPrizesList.append($el);
  });
  // this is custom code to define our non-standard interface with the web server
  function ioServer() {
    let last_win = 0;

    // this emulates PrizesAndReels::ReelsForPrizeID
    // which the PHP would call from RandomLogic::PrizeAndReels after selecting a prize
    function getReelsForPayout(payout) {
      function getParsedRules(payout) {
        if (!payout)
          return { rules: { reel1: "*", reel2: "*", reel3: "*" } };
        last_win = payout;
        const prize = prizes.find((it) => it.payout_winnings == payout);
        if (!prize) throw `Unsupported prize '${payout}'`;
        return {
          id: prize.id,
          rules: {
            reel1: prize.reel1_parsed,
            reel2: prize.reel2_parsed,
            reel3: prize.reel3_parsed,
          },
        };
      }
      function forcedReelOutcome(rule) {
        if (rule === "*") rule = ["1", "2", "3", "4", "5", "6"];
        if (!Array.isArray(rule)) return rule;
        return rule[Math.floor(Math.random() * rule.length)];
      }
      function payoutForReels(reels) {
        function compareReel(outcome, rule) {
          return Array.isArray(rule)
            ? rule.find((option) => option === outcome)
            : rule === outcome;
        }
        const found = prizes.find(
          (prize) =>
            compareReel(reels[0], prize.reel1_parsed) &&
            compareReel(reels[1], prize.reel2_parsed) &&
            compareReel(reels[2], prize.reel3_parsed)
        );
        return found ? found.payout_credits : 0;
      }

      const { rules, id } = getParsedRules(payout);
      for (let i = 0; i < 1000; ++i) {
        const reels = [
          forcedReelOutcome(rules.reel1),
          forcedReelOutcome(rules.reel2),
          forcedReelOutcome(rules.reel3),
        ];
        if (payoutForReels(reels) == payout) return { id, reels };
      }
      throw `Failed to find reels for '${payout}'`;
    }

    function getResultReels(result) {
      const { reels, id } = getReelsForPayout(
        result.prize ? result.prize.payout_credits : 0
      );
      result.reels = reels;
      if (id) result.prize.id = id;
      result.last_win = last_win;
      return result;
    }

    function ajaxRequest(params, type, url, fnSuccess, fnError) {
      $.ajax({
        url: url,
        type: type,
        data: params,
        dataType: "json",
        timeout: DEFAULT_TIMEOUT,
        success: function (data) {
          fnSuccess(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
          const msg =
            jqXHR.responseText
              ? jqXHR.responseText
              : textStatus && errorThrown
              ? `${textStatus} -- ${errorThrown}`
              : textStatus
              ? textStatus
              : errorThrown
              ? "" + errorThrown
              : "Error";
          return fnError(msg);
        },
      });
    }

    // in future there may be additional parameters e.g. for the size of the bet
    function spin(curBet, fnSuccess, fnError) {
      const onSuccess = (spinResult) => {
        if (!spinResult.success) {
          fnError();
          return;
        }
        try {
          spinResult = getResultReels(spinResult);
          fnSuccess(spinResult);
        } catch (err) {
          fnError();
        }
      };
      ajaxRequest({ name: accountName, wager: curBet }, "POST", "/spin", onSuccess, fnError);
    }

    function create(accountName, fnSuccess, fnError) {
      ajaxRequest({ name: accountName }, "POST", "/create", fnSuccess, fnError);
    }

    function balance(accountName, fnSuccess, fnError) {
      ajaxRequest({ name: accountName }, "GET", "/balance", fnSuccess, fnError);
    }

    return { ajaxRequest, spin, create, balance };
  }
  window.ioServer = ioServer();
})();
