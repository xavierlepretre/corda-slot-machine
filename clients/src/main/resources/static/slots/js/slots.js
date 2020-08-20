"use strict";

var SlotMachines = {
	balance: window.remaining_balance,

	config: {
		// Set the proper height for the reels in the CSS file, rule: .slot_machine_container .slot_machine_reel_container .slot_machine_reel
		// Set it to 3 * stripHeight
		// Also set the top property to the initial position you want to show
		stripHeight: 720, // Update this to match the strip PNG
		alignmentOffset: 86, // Play around with this until reels are properly aligned post-spin

		firstReelStopTime: 667,
		secondReelStopTime: 575, // since first reel's stop time, not since animation beginning
		thirdReelStopTime: 568, // since second reel's stop time, not since animation beginning
		payoutStopTime: 700, // since last reel's stop time, not since animation beginning

		reelSpeedDifference: 0, // speed difference between the 3 reels
		reelSpeed1Delta: 100, // "Fast speed"
		reelSpeed1Time: 0, // How long does fast speed lasts.
		reelSpeed2Delta: 100, // Slow speed

		positioningTime: 200,
		bounceHeight: 200,
		bounceTime: 1000,

		winningsFormatPrefix: '',  // If winnings are "money", set prefix to be '$', '£', etc. If everything is unit-less, leave as is.

		actionURL: 'slots_action.php', // point to the server component to call to get spin results.
	}
}

function SlotMachinesBalanceChange(balance) {
	// Add here the code to update the balance on screen
}

//======================================

var SlotMachine = function($elSlotsOuterContainer) {
	var _this = this;
	var gameSettings = $elSlotsOuterContainer.data("game-settings");

	this.gameType = gameSettings.game_type;
	this.iconsPerReel = parseInt(gameSettings.icons_per_reel, 10);
	this.minBet = parseFloat(gameSettings.min_bet, 10);
	this.maxBet = parseFloat(gameSettings.max_bet, 10);
	this.curBet = this.minBet;
	this.spinning = false;
	this.numReels = 3;

	// Find all the elements
	this.$elSlotsOuterContainer = $elSlotsOuterContainer;
	this.$elPrizesList = $elSlotsOuterContainer.find('.slot_machine_prizes_list');
	this.$elReels = [null]; // Leave index 0 empty so we can refer to reels as 1,2,3
	for (var i = 1; i <= this.numReels; i++) {
		this.$elReels.push($elSlotsOuterContainer.find('.slot_machine_reel' + i))
	}
	this.$elOutputBalance = $elSlotsOuterContainer.find('.slot_machine_output_balance');
	this.$elOutputDayWinnings = $elSlotsOuterContainer.find('.slot_machine_output_day_winnings');
	this.$elOutputLifetimeWinnings = $elSlotsOuterContainer.find('.slot_machine_output_lifetime_winnings');
	this.$elOutputBet = $elSlotsOuterContainer.find('.slot_machine_output_bet');
	this.$elOutputLastWin = $elSlotsOuterContainer.find('.slot_machine_output_last_win');
	this.$elBetIncreaseButton = $elSlotsOuterContainer.find('.slot_machine_bet_increase_button');
	this.$elBetDecreaseButton = $elSlotsOuterContainer.find('.slot_machine_bet_decrease_button');
	this.$elSpinButton = $elSlotsOuterContainer.find('.slot_machine_spin_button');
	this.$elSoundToggleButton = $elSlotsOuterContainer.find('.slot_machine_sound_toggle_button');

	// Bind events
	this.$elBetIncreaseButton.click(function() { _this.changeBet(+1); });
	this.$elBetDecreaseButton.click(function() { _this.changeBet(-1); });
	this.$elSpinButton.click(function() { _this.spin(); });
	this.$elSoundToggleButton.click(function() { _this.toggleSound(); });

	// Update visual state
	this.$elOutputBalance.html(SlotMachines.balance);
	this.$elOutputBet.html(this.curBet);

	if (SlotMachines.balance < this.minBet) {
		this.disableSpinButton();
	}
};

//----------------------------------------------------
// UI handling

// Process clicking in bet up/down buttons
SlotMachine.prototype.changeBet = function(delta) {
	var _this = this;

	if (this.spinning) { return; } // don't do anything while spinning.

	this.curBet += delta;
	this.curBet = Math.min(this.curBet, this.maxBet);
	this.curBet = Math.min(this.curBet, SlotMachines.balance); // Don't allow higher bet than current balance
	this.curBet = Math.max(this.minBet, this.curBet); // But don't allow = 0 either

	this.showWonState(false); // Remove won state, so that they can't easily fake a screenshot to say "I bet 2 and got paid off only as 1"

	this.$elOutputBet.html(this.curBet);

	// Update payouts in prizes list
	this.$elPrizesList.find('.slot_machine_prize_payout').each(function() {
		var $tdPayout = $(this);
		// TODO: proper formatting function here
		$tdPayout.html(
			($tdPayout.attr("data-payoutPrefix") || "") + parseFloat($tdPayout.attr("data-basePayout"), 10) * _this.curBet + ($tdPayout.attr("data-payoutSuffix") || "")
		);
	});

	if (SlotMachines.balance >= this.curBet) {
		this.enableSpinButton();
	}
};

SlotMachine.prototype.disableSpinButton = function() {
	this.$elSpinButton.addClass("disabled");
};

SlotMachine.prototype.enableSpinButton = function() {
	this.$elSpinButton.removeClass("disabled");
};

SlotMachine.prototype.toggleSound = function() {
	SlotsSounds.toggleMute();
	this.$elSoundToggleButton.toggleClass("off");
};

// Turn on / off the "won" state for the slot machine (the banner at the top),
// and for the prize that was won
SlotMachine.prototype.showWonState = function(bWon, prize_id) {
	if (bWon) {
		this.$elSlotsOuterContainer.addClass("won");
		this.$elPrizesList.find('.slot_machine_prize_row_' + prize_id).addClass("won");
	} else {
		this.$elSlotsOuterContainer.removeClass("won");
		this.$elPrizesList.find('.slot_machine_prize_row').removeClass("won");
		this.$elOutputLastWin.html("");
	}
};

// Given a set of counters we got from the server after a spin, update all the UI counters
// to those values, to make sure everything is up to date and in sync.
// We do this after we finish the animation that increases them gradually, or once we
// stopped the reels, if there was no prize.
// This is *technically* redundant, but it ensures we're up to date with the server
SlotMachine.prototype.updateCountersToLatest = function(spinResult) {
	this.$elOutputBalance.html(SlotMachines.balance);
	if (typeof spinResult.day_winnings != "undefined") {
		// TODO: Fix this formatting
		this.$elOutputDayWinnings.html(SlotMachines.config.winningsFormatPrefix + this.formatWinningsNumber(spinResult.day_winnings));
	}
	if (typeof spinResult.lifetime_winnings != "undefined") {
		// TODO: Fix this formatting
		this.$elOutputLifetimeWinnings.html(SlotMachines.config.winningsFormatPrefix + this.formatWinningsNumber(spinResult.lifetime_winnings));
	}

	// TODO!!
	if (typeof spinResult.last_win != "undefined") {
		$('.slot_machine_output_last_win').html(spinResult.last_win);
	}
};

// TODO: Make this more flexible, with prefix and suffix, etc. ALso for both balnce and winnings
SlotMachine.prototype.formatWinningsNumber = function(winnings) {
	if (winnings == Math.floor(winnings)) {
		return winnings;
	} else {
		return winnings.toFixed(2);
	}
};

//----------------------------------------------------
// Spin Start / End functionality

SlotMachine.prototype.spin = function() {
	var _this = this;

	// Validate that we can spin
	if (this.$elSpinButton.hasClass("disabled")) { return false; }
	if (this.spinning) { return false; }

	// Clean up the UI
	this.spinning = true;
	this.showWonState(false);
	this.disableSpinButton();

	// Deduct the bet from the balance
	SlotMachines.balance -= this.curBet;
	SlotMachinesBalanceChange(SlotMachines.balance);
	this.$elOutputBalance.html(SlotMachines.balance);

	// Make the reels spin
	this.startSpinningReels();
	SlotsSounds.playSound('spinning');

	// We need to make the reels end spinning at a certain time, synched with the audio, independently of how long the AJAX request takes.
	// Also, we can't stop until the AJAX request comes back. So we must have a timeout for the first reel stop, and a function that makes
	//   the magic happen, and whatever happens last (this timeout, or the AJAX response) calls this function.
	// The sound timings are at: 917ms, 1492ms and 2060ms, which needs to be adjusted by the animation timings
	//   (which is why the first one is set at 250ms before 917ms)
	var firstReelTimeoutHit = false;
	var spinResult = null;

	var fnFirstReelTimeout = function(){
		firstReelTimeoutHit = true;
		if (spinResult != null) { _this.stopReelsAndEndSpin(spinResult); } // If AJAX came back already, stop reels (this is the ideal case)
	}
	window.setTimeout(fnFirstReelTimeout, SlotMachines.config.firstReelStopTime);

	var fnAJAXRequestSuccess = function(data){
		spinResult = data;
		if (firstReelTimeoutHit == true) { _this.stopReelsAndEndSpin(spinResult); } // First reel should have stopped already, we are late
	}
	this.makeRequest(
		{ action: 'spin', bet : this.curBet, window_id: window.windowID, game_type: this.gameType},
		fnAJAXRequestSuccess
	);
};

// Called once the first reel needs to stop (first reel timeout has hit *and* we got our
// response from the server).
// Makes the reels stop, and then calls endSpin to deal with updating UI
SlotMachine.prototype.stopReelsAndEndSpin = function(spinResult) {
	 var _this = this;

	 // Make the reels stop spinning one by one
	 var baseTimeout = 0;
	 window.setTimeout(function(){ _this.stopReel(1, spinResult.reels[0]); }, baseTimeout);
	 baseTimeout += SlotMachines.config.secondReelStopTime;
	 window.setTimeout(function(){ _this.stopReel(2, spinResult.reels[1]); }, baseTimeout);
	 baseTimeout += SlotMachines.config.thirdReelStopTime;
	 window.setTimeout(function(){ _this.stopReel(3, spinResult.reels[2]); }, baseTimeout);

	 baseTimeout += SlotMachines.config.payoutStopTime; // This must be related to the timing of the final animation. Make it a bit less, so the last reel is still bouncing when it lights up
	 window.setTimeout(function(){ _this.endSpin(spinResult); }, baseTimeout);
};

// Once reels have stopped turning, turn on the Win sign and start the output counters 
// counting up, show the result of the spin, or just instantly update all counters to latest
// state if there was no prize 
 SlotMachine.prototype.endSpin = function(spinResult) {
	 if (spinResult.prize != null) {
		 this.showWonState(true, spinResult.prize.id);
		 this.incrementOutputCounters(spinResult); // incrementOutputCounters will call end_spin_after_payout, which is where this list of things to do at the end really ends
	 } else {
		 this.endSpinAfterCountersUpdated(spinResult);
	 }
 };

// These are the things that need to be done after the payout counter stops increasing, if there is a payout
SlotMachine.prototype.endSpinAfterCountersUpdated = function(spinResult) {
	this.spinning = false;
	SlotMachines.balance = spinResult.balance;
	SlotMachinesBalanceChange(SlotMachines.balance);

	if (SlotMachines.balance >= this.curBet) {
		this.enableSpinButton();
	}

	// This is technically redundant, since the payout incrementer updated them, and we decreased it when spinning,
	//   but just in case something got off sync
	this.updateCountersToLatest(spinResult);
};

// Somethign went wrong, stop all reels where they are
SlotMachine.prototype.abortSpinAbruptly = function() {
	this.stopReel(1, null);
	this.stopReel(2, null);
	this.stopReel(3, null);
	SlotsSounds.stopSound("spinning");
};

SlotMachine.prototype.makeRequest = function(params, fnSuccess) {
	var _this = this;

	$.ajax({
		url: SlotMachines.config.actionURL,
		type: "POST",
		data: params,
		dataType: "json",
		timeout: 10000,
		success: function(data){
			if (!data.success) {
				return _this.handleServerError(data);
			}
			fnSuccess(data);
		},
		error: function() {
			return _this.handleServerError({});
		}
	});
};

SlotMachine.prototype.handleServerError = function(data) {
	// TODO: See what scratchcards do here
	this.abortSpinAbruptly();
	if (data.error == "loggedOut") {
		this.$elSlotsOuterContainer.find('.slot_machine_logged_out_message').show();
	} else {
		this.$elSlotsOuterContainer.find('.slot_machine_failed_request_message').show();
	}
	return false;
}

//----------------------------------------------------
// Animation

SlotMachine.prototype.startSpinningReels = function() {
	this.spinReel(1, 0);
	this.spinReel(2, SlotMachines.config.secondReelStopTime);
	this.spinReel(3, SlotMachines.config.secondReelStopTime + SlotMachines.config.thirdReelStopTime);
};

// Kick off and run the reel spin animation
// timeOffset is how much time later than the previous reel we expect this reel to stop spinning.
SlotMachine.prototype.spinReel = function(i, timeOffset) {
	var startTime = Date.now();
	var $elReel = this.$elReels[i];
	$elReel.css({top: -(Math.random() * SlotMachines.config.stripHeight * 2) }); // Change the initial position so that, if a screenshot is taken mid-spin, reels are mis-aligned
	var curPos = parseInt($elReel.css("top"), 10);

	var fnAnimation = function(){
		$elReel.css({top: curPos});

		// Choose between fast and slow movements
		if (Date.now() < startTime + SlotMachines.config.reelSpeed1Time + timeOffset) {
			curPos += SlotMachines.config.reelSpeed1Delta;
		} else {
			curPos += SlotMachines.config.reelSpeed2Delta;
		}
		curPos += i * SlotMachines.config.reelSpeedDifference;
		if (curPos > 0) {curPos = -SlotMachines.config.stripHeight * 2;}
	};
	var timerID = window.setInterval(fnAnimation, 20);
	$elReel.data("spinTimer", timerID); // Store the inerval timer so we can kill it when stopping
};

// Stop reel with nice bouncy animation
// Outcome is what position it landed on (1..iconsPerReel)
// If outcome is null, we stop abruptly where we are (only happens in errors)
SlotMachine.prototype.stopReel = function(i, outcome) {
	var $elReel = this.$elReels[i];
	var timerID = $elReel.data("spinTimer");
	window.clearInterval(timerID);
	$elReel.data("spinTimer", null);

	if (outcome != null) {
		// the whole strip repeats thrice, so we don't have to care about looping
		// alignmentOffset is kind of empirical...
		var distanceBetweenIcons = SlotMachines.config.stripHeight / this.iconsPerReel;
		var finalPosition = -SlotMachines.config.stripHeight -((outcome - 1) * distanceBetweenIcons) + SlotMachines.config.alignmentOffset;

		// Animation two: Elastic Easing
		$elReel.css({ top: finalPosition - SlotMachines.config.stripHeight })
			.animate({ top: finalPosition + SlotMachines.config.bounceHeight}, SlotMachines.config.positioningTime, 'linear', function() {
				$elReel.animate({top: finalPosition}, SlotMachines.config.bounceTime, 'easeOutElastic');
			});
	}
};

// Animate the gradual counting up of all the counters, until we reach the latest values
// the server gave us.
SlotMachine.prototype.incrementOutputCounters = function(spinResult) {
	var _this = this;
	
	// We derive what the current value of the counters *should* be, based on the latest
	// values we got from the server, and the prize we got.
	// This should be the same as the value that is showing already, but if for some reason
	// it isn't, this ends up with us in sync.
	var currentValues = {
		balance: spinResult.balance - spinResult.prize.payout_credits,
		day_winnings: spinResult.day_winnings - spinResult.prize.payout_winnings,
		lifetime_winnings: spinResult.lifetime_winnings - spinResult.prize.payout_winnings,
	}

	var outputElements = {
		balance: this.$elOutputBalance,
		day_winnings: this.$elOutputDayWinnings,
		lifetime_winnings: this.$elOutputLifetimeWinnings
	}

	// If winnings and credits are different, pick the largest to increase
	var maxDelta = Math.max(spinResult.balance - currentValues.balance, spinResult.day_winnings - currentValues.day_winnings);

	var soundName = (maxDelta > 80 ? 'fastpayout' : 'payout' );
	SlotsSounds.playSound(soundName);

	var tickDelay = (maxDelta > 80 ? 50 : 200 );
	var timerID = window.setInterval(function() {
		var valueChanged = false;
		$.each(['balance', 'day_winnings', 'lifetime_winnings'], function(i, component){
			if (currentValues[component] < spinResult[component]) {
				currentValues[component] += 1;
				currentValues[component] = Math.min(currentValues[component], spinResult[component]); // make sure we don't go over, useful for decimals.

				// TODO: fix this formatting travesty
				var formattedValue = currentValues[component];
				if (component != "balance") {
					formattedValue = SlotMachines.config.winningsFormatPrefix + _this.formatWinningsNumber(currentValues[component]);
				}
				outputElements[component].html(formattedValue)
				valueChanged = true;
			}
		});

		if (!valueChanged) { // we're done updating all the counters
			window.clearInterval(timerID);
			SlotsSounds.stopSound(soundName);
			_this.endSpinAfterCountersUpdated(spinResult);
		}
	}, tickDelay);
};

//======================================

var SlotsSounds = {
	sounds: {},
	soundEnabled: true,

	init: function() {
		soundManager.setup({
			url: "js/",
			debugMode: false,
			onready: function() {
				SlotsSounds.sounds['payout'] = soundManager.createSound({
					id: "payout",
					url: 'sounds/payout.mp3',
					loops: 9999,
					autoLoad: true
				});
				SlotsSounds.sounds['fastpayout'] = soundManager.createSound({
					id: "fastpayout",
					url: 'sounds/fastpayout.mp3',
					loops: 9999,
					autoLoad: true
				});
				SlotsSounds.sounds['spinning'] = soundManager.createSound({
					id: "spinning",
					url: 'sounds/spinning.mp3',
					autoLoad: true
				});
			}
		});
	},

	playSound: function(sound_id) {
		if (SlotsSounds.soundEnabled) {
			try {
				SlotsSounds.sounds[sound_id].play();
			} catch(err) {}
		}
	},

	stopSound: function(sound_id) {
		try {
			SlotsSounds.sounds[sound_id].stop();
		} catch(err) {}
	},

	mute: function() {
		SlotsSounds.soundEnabled = false;
		soundManager.mute();
	},

	unmute: function() {
		SlotsSounds.soundEnabled = true;
		soundManager.unmute();
	},

	toggleMute: function() {
		if (SlotsSounds.soundEnabled) {
			SlotsSounds.mute();
		} else {
			SlotsSounds.unmute();
		}
	}
};


//======================================

$(window).on("load", function() {
	SlotsSounds.init();

	$(".slot_machine_outer_container").each(function(){
		new SlotMachine($(this));
	});
});
